package com.b2bfintech.app.service;

import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.platform.service.AuthSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Permanently deletes a corporate organisation, all of its users, sessions, branches,
 * bills, bill fetches, bill payments, disputes, wallet ledger entries, support tickets,
 * documents, and category configurations after verifying the Super Admin password.
 */
@Service
public class CorporateDeletionService {

    private final JdbcTemplate jdbcTemplate;
    private final ClientService clientService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final AuditService auditService;
    private final String configuredSuperAdminPassword;

    public CorporateDeletionService(
            JdbcTemplate jdbcTemplate,
            ClientService clientService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            AuditService auditService,
            @Value("${app.auth.platform-admin-password:}") String configuredSuperAdminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientService = clientService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.auditService = auditService;
        this.configuredSuperAdminPassword = configuredSuperAdminPassword;
    }

    @Transactional
    public void deleteCorporateCompletely(String corpId, String rawPassword, AuthenticatedUser principal) {
        if (principal == null || !"SUPER_ADMIN".equals(principal.role())) {
            throw new AccessDeniedException("Only Super Admin can delete a corporate");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadRequestException("Enter the Super Admin password to confirm deletion");
        }
        verifySuperAdminPassword(rawPassword, principal);

        Client client = clientService.getById(corpId);
        if (client.getClientType() != Client.ClientType.CORPORATE) {
            throw new BadRequestException("Only a corporate account can be deleted");
        }
        String corpName = client.getClientName();

        List<AppUser> corpUsers = appUserRepository.findByUserOrgId(corpId);
        authSessionService.revokeAllForClient(corpId, "CORPORATE_DELETED");
        for (AppUser user : corpUsers) {
            authSessionService.revokeAllForUser(user.getUserId(), "CORPORATE_DELETED");
        }

        // 1. Delete children of bill_payment and bills for this corporate
        String corpBillsSubquery =
                "SELECT bill_id FROM bills WHERE source_id = ? "
                + "OR branch_id IN (SELECT branch_id FROM branch WHERE branch_source_id = ?) "
                + "OR user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)";
        String corpFetchesSubquery =
                "SELECT bill_fetch_id FROM bill_fetch WHERE bill_id IN (" + corpBillsSubquery + ")";
        String corpPaymentsSubquery =
                "SELECT id FROM bill_payment WHERE corp_id = ? "
                + "OR bill_id IN (" + corpBillsSubquery + ") "
                + "OR bill_fetch_id IN (" + corpFetchesSubquery + ")";

        jdbcTemplate.update(
                "DELETE FROM vendor_payments WHERE bill_payment_id IN (" + corpPaymentsSubquery + ")",
                corpId, corpId, corpId, corpId, corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bill_dispute WHERE bill_payment_id IN (" + corpPaymentsSubquery + ")",
                corpId, corpId, corpId, corpId, corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bill_payment_rejection WHERE bill_payment_id IN (" + corpPaymentsSubquery + ")",
                corpId, corpId, corpId, corpId, corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bill_payment_log WHERE bill_id IN (" + corpBillsSubquery + ") "
                + "OR bill_payment_id IN (" + corpPaymentsSubquery + ")",
                corpId, corpId, corpId, corpId, corpId, corpId, corpId, corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bill_payment WHERE id IN (" + corpPaymentsSubquery + ")",
                corpId, corpId, corpId, corpId, corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bill_fetch WHERE bill_id IN (" + corpBillsSubquery + ")",
                corpId, corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM bills WHERE bill_id IN (" + corpBillsSubquery + ")",
                corpId, corpId, corpId);

        // 2. Delete wallet, ledger transactions, platform transactions, and top-up requests
        jdbcTemplate.update(
                "DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM wallet WHERE wallet_user_id = ?)",
                corpId);
        jdbcTemplate.update(
                "DELETE FROM platform_transaction WHERE wallet_id IN (SELECT id FROM wallet WHERE wallet_user_id = ?)",
                corpId);
        jdbcTemplate.update(
                "DELETE FROM wallet_transaction_request WHERE corp_id = ?",
                corpId);
        jdbcTemplate.update(
                "DELETE FROM wallet WHERE wallet_user_id = ?",
                corpId);

        // 3. Delete support tickets, messages, and read receipts
        jdbcTemplate.update(
                "DELETE FROM support_ticket_read WHERE ticket_id IN (SELECT id FROM support_ticket WHERE client_id = ?) "
                + "OR user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)",
                corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM support_ticket_message WHERE ticket_id IN (SELECT id FROM support_ticket WHERE client_id = ?) "
                + "OR author_user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)",
                corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM support_ticket WHERE client_id = ?",
                corpId);

        // 4. Delete branch user mappings and branches
        jdbcTemplate.update(
                "DELETE FROM branch_user_mapping WHERE branch_id IN (SELECT branch_id FROM branch WHERE branch_source_id = ?) "
                + "OR user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)",
                corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM branch WHERE branch_source_id = ?",
                corpId);

        // 5. Delete category configs, documents, auth sessions, and audit logs for this corporate & its users
        jdbcTemplate.update("DELETE FROM client_category_config WHERE client_id = ?", corpId);
        jdbcTemplate.update("DELETE FROM document WHERE owner_id = ?", corpId);
        jdbcTemplate.update(
                "DELETE FROM auth_session WHERE client_id = ? OR user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)",
                corpId, corpId);
        jdbcTemplate.update(
                "DELETE FROM audit_log WHERE (entity_type = 'Client' AND entity_id = ?) "
                + "OR actor_user_id IN (SELECT user_id FROM app_user WHERE user_org_id = ?)",
                corpId, corpId);

        // 6. Delete all users of this corporate and the corporate row itself
        jdbcTemplate.update("DELETE FROM app_user WHERE user_org_id = ?", corpId);
        jdbcTemplate.update("DELETE FROM corp_details WHERE corp_id = ?", corpId);

        auditService.record(
                "CORPORATE_DELETED",
                "Client",
                corpId,
                "Permanently deleted corporate " + corpName + " (" + corpId + ") and all related users and data");
    }

    private void verifySuperAdminPassword(String rawPassword, AuthenticatedUser principal) {
        boolean matchesProperty = configuredSuperAdminPassword != null
                && !configuredSuperAdminPassword.isBlank()
                && configuredSuperAdminPassword.equals(rawPassword);
        if (matchesProperty) {
            return;
        }
        boolean matchesStoredUser = principal.userId() != null
                && appUserRepository.findById(principal.userId())
                        .filter(AppUser::isSuperAdmin)
                        .map(user -> user.getPassword() != null
                                && !user.getPassword().isBlank()
                                && passwordEncoder.matches(rawPassword, user.getPassword()))
                        .orElse(false);
        if (!matchesStoredUser) {
            throw new BadRequestException("Incorrect Super Admin password");
        }
    }
}
