package com.b2bfintech.app.controller;

import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.ClientOnboarding;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.OnboardingService;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.service.WalletService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Organisation admin manages the lifecycle of its clients (implementation plan &sect;4.2/&sect;4.3). */
@RestController
@RequestMapping("/api/v1/paytrix/clients")
public class ClientController {

    private static final Set<String> ASSIGNABLE_CLIENT_ROLES =
            Set.of("CLIENT_ADMIN", "CLIENT_OPERATOR", "BRANCH_USER");

    private final ClientService clientService;
    private final TenantUserProvisioningService provisioningService;
    private final TenantUserService tenantUserService;
    private final WalletService walletService;
    private final AuditService auditService;
    private final OnboardingService onboardingService;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final BranchService branchService;

    public ClientController(
            ClientService clientService,
            TenantUserProvisioningService provisioningService,
            TenantUserService tenantUserService,
            WalletService walletService,
            AuditService auditService,
            OnboardingService onboardingService,
            AppUserRepository appUserRepository,
            BranchRepository branchRepository,
            BranchService branchService) {
        this.clientService = clientService;
        this.provisioningService = provisioningService;
        this.tenantUserService = tenantUserService;
        this.walletService = walletService;
        this.auditService = auditService;
        this.onboardingService = onboardingService;
        this.appUserRepository = appUserRepository;
        this.branchRepository = branchRepository;
        this.branchService = branchService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClientDto create(@Valid @RequestBody CreateClientRequest request,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        Client client = clientService.create(
                request.clientName(),
                request.phoneNumber(),
                request.email(),
                request.clientType(),
                request.corporateKind(),
                request.contactPersonName());
        if (principal != null && principal.userId() != null) {
            client.setOnboardedBy("ORG_ADMIN");
            client.setOnboardedById(principal.userId());
            client = clientService.save(client);
        }
        onboardingService.ensure(client.getId());
        if (client.getClientType() == Client.ClientType.VENDOR) {
            if (request.password() != null && !request.password().isBlank()) {
                if (request.password().length() < 6 || request.password().length() > 72) {
                    throw new BadRequestException("Password must be 6 to 72 characters");
                }
                provisioningService.createClientUser(
                        request.phoneNumber(), request.clientName(), "CLIENT_ADMIN", client, request.email(), request.password());
            }
            if (request.openingBalance() != null && request.openingBalance().signum() > 0) {
                throw new BadRequestException("A vendor does not have a wallet");
            }
        } else {
            creditOpeningBalance(client, request.openingBalance(), principal == null ? null : principal.userId());
        }
        return toDto(client);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<ClientDto> list() {
        Map<String, Long> branchCounts = new HashMap<>();
        for (BranchRepository.BranchCount row : branchRepository.countGroupedBySourceId()) {
            branchCounts.put(row.getSourceId(), row.getBranchCount());
        }
        return clientService.listAll().stream()
                .map(client -> toDto(client, branchCounts.getOrDefault(client.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public ClientDto get(@PathVariable String id) {
        return toDto(clientService.getById(id));
    }

    @PatchMapping("/{id}/sales-person")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientDto assignSalesPerson(@PathVariable String id, @RequestBody SalesPersonRequest request) {
        return toDto(clientService.assignSalesPerson(id, parseSalesUserId(request.salesUserId())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientDto updateDetails(@PathVariable String id, @Valid @RequestBody UpdateClientDetailsRequest request) {
        return toDto(clientService.updateDetails(
                id,
                request.clientName(),
                request.corporateKind(),
                request.contactPersonName(),
                request.contactEmail(),
                request.contactPhone()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientDto setStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        Client.ClientStatus status = Client.ClientStatus.valueOf(request.status());
        if (status == Client.ClientStatus.ACTIVE) {
            onboardingService.assertCanActivate(id);
        }
        return toDto(clientService.setStatus(id, status));
    }

    /** Adds another staff member (admin or operator) to an already-onboarded client. */
    @PostMapping("/{id}/users")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientUserDto addUser(@PathVariable String id, @Valid @RequestBody AddClientUserRequest request) {
        if (!ASSIGNABLE_CLIENT_ROLES.contains(request.role())) {
            throw new BadRequestException("role must be one of " + ASSIGNABLE_CLIENT_ROLES);
        }
        String rawPassword = (request.password() == null || request.password().isBlank()) ? null : request.password();
        if (rawPassword != null && (rawPassword.length() < 6 || rawPassword.length() > 72)) {
            throw new BadRequestException("Password must be 6 to 72 characters");
        }
        Client client = clientService.getById(id);
        if (client.getStatus() != Client.ClientStatus.ACTIVE) {
            throw new BadRequestException(client.getClientType() == Client.ClientType.VENDOR
                    ? "Mark the vendor as active before creating users"
                    : "Mark the corporate as active before creating users");
        }
        String rawEmail = (request.email() == null || request.email().isBlank()) ? null : request.email().trim();
        AppUser user = provisioningService.createClientUser(
                request.phoneNumber(), request.fullName(), request.role(), client, rawEmail, rawPassword);
        List<Branch> assigned = List.of();
        if (client.getClientType() == Client.ClientType.CORPORATE
                && request.branchId() != null
                && !request.branchId().isBlank()) {
            branchService.assignUser(id, request.branchId().trim(), user.getUserId());
            assigned = branchService.branchesForUser(id, user.getUserId());
        }
        return ClientUserDto.from(user, branchService.screenRole(user), assigned);
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<ClientUserDto> listUsers(@PathVariable String id) {
        Client client = clientService.getById(id);
        boolean corporate = client.getClientType() == Client.ClientType.CORPORATE;
        return tenantUserService.listForClient(id).stream()
                .map(user -> ClientUserDto.from(
                        user,
                        branchService.screenRole(user),
                        corporate ? branchService.branchesForUser(id, user.getUserId()) : List.of()))
                .toList();
    }

    @PutMapping("/{id}/users/{userId}/branches")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientUserDto updateUserBranches(
            @PathVariable String id,
            @PathVariable String userId,
            @RequestBody UpdateUserBranchesRequest request) {
        AppUser user = requireClientUser(id, userId);
        List<Branch> assigned = branchService.setBranchesForUser(id, userId, request.resolvedBranchIds());
        return ClientUserDto.from(user, branchService.screenRole(user), assigned);
    }

    @PatchMapping("/{id}/users/{userId}/suspend")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void suspendUser(@PathVariable String id, @PathVariable String userId) {
        AppUser target = requireClientUser(id, userId);
        if (isLastActiveAdmin(id, target)) {
            throw new BadRequestException("Cannot suspend the last active Client Admin");
        }
        provisioningService.suspendUser(userId);
    }

    @PatchMapping("/{id}/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void reactivateUser(@PathVariable String id, @PathVariable String userId) {
        requireClientUser(id, userId);
        provisioningService.reactivateUser(userId);
    }

    private AppUser requireClientUser(String clientId, String userId) {
        AppUser user = tenantUserService.getById(userId);
        if (user.organisationId() == null || !clientId.equals(user.organisationId())) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private boolean isLastActiveAdmin(String clientId, AppUser target) {
        if (!"CLIENT_ADMIN".equals(target.getRole().getName())) return false;
        return tenantUserService.listForClient(clientId).stream()
                .filter(user -> "CLIENT_ADMIN".equals(user.getRole().getName()))
                .filter(user -> user.getStatus() == AppUser.UserStatus.ACTIVE)
                .count() <= 1;
    }

    public record AddClientUserRequest(
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @NotBlank String fullName,
            @NotBlank String role,
            String email,
            String password,
            String branchId) {
    }

    public record UpdateUserBranchesRequest(String branchId, List<String> branchIds) {
        public List<String> resolvedBranchIds() {
            if (branchIds != null) {
                return branchIds;
            }
            if (branchId != null && !branchId.isBlank()) {
                return List.of(branchId.trim());
            }
            return List.of();
        }
    }

    public record UserBranchDto(String id, String name, String code) {
        static UserBranchDto from(Branch b) {
            return new UserBranchDto(b.getId(), b.getName(), b.getCode());
        }
    }

    public record ClientUserDto(
            String id,
            String phoneNumber,
            String email,
            String userCode,
            String fullName,
            String role,
            String status,
            List<UserBranchDto> branches) {
        static ClientUserDto from(AppUser u) {
            return from(u, u.getRole() == null ? null : u.getRole().getName(), List.of());
        }

        static ClientUserDto from(AppUser u, String role) {
            return from(u, role, List.of());
        }

        static ClientUserDto from(AppUser u, String role, List<Branch> branches) {
            return new ClientUserDto(
                    u.getId().toString(),
                    u.getPhoneNumber(),
                    u.getEmail(),
                    u.getUserCode(),
                    u.getFullName(),
                    role,
                    u.getStatus().name(),
                    branches == null ? List.of() : branches.stream().map(UserBranchDto::from).toList());
        }
    }

    /**
     * The client's phone number doubles as both its contact number and its first admin's login
     * phone. The password registers against the phone number (and email, if given).
     */
    public record CreateClientRequest(
            @NotBlank String clientName,
            String contactPersonName,
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @Email String email,
            String password,
            @NotNull Client.ClientType clientType,
            Client.CorporateKind corporateKind,
            @DecimalMin(value = "0.00", inclusive = true) @Digits(integer = 12, fraction = 2) BigDecimal openingBalance) {
    }

    private void creditOpeningBalance(Client client, BigDecimal openingBalance, String userId) {
        if (openingBalance == null || openingBalance.signum() <= 0) {
            return;
        }
        walletService.credit(client.getId(), openingBalance, LedgerEntry.RECHARGE, userId);
        auditService.record("WALLET_OPENING_CREDIT", "Client", client.getId().toString(),
                "credited opening balance " + openingBalance);
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record SalesPersonRequest(String salesUserId) {
    }

    public record UpdateClientDetailsRequest(
            @NotBlank String clientName,
            Client.CorporateKind corporateKind,
            String contactPersonName,
            String contactEmail,
            String contactPhone) {
    }

    public record ClientDto(
            String id, String clientName, String clientCode, String contactPhone, String contactEmail, String contactPersonName, String clientType,
            String corporateKind, String status,
            Instant createdAt, String kycStatus, String contractStatus, boolean detailsSubmitted,
            String salesUserId, String salesPersonName, long branchCount) {
        static ClientDto from(Client a, ClientOnboarding onboarding, String salesPersonName, long branchCount) {
            return new ClientDto(a.getId().toString(), a.getClientName(), a.getClientCode(), a.getContactPhone(),
                    a.getEmail(),
                    a.getSigningAuthority(),
                    a.getClientType().name(),
                    a.getCorporateKind() == null ? null : a.getCorporateKind().name(),
                    a.getStatus().name(), a.getCreatedAt(),
                    onboarding.getKycStatus().name(), onboarding.getContractStatus().name(),
                    onboarding.isDetailsSubmitted(),
                    a.getSalesUserId(),
                    salesPersonName, branchCount);
        }
    }

    private ClientDto toDto(Client client) {
        long branchCount = client.getClientType() == Client.ClientType.CORPORATE
                ? branchRepository.countBySourceId(client.getId().toString())
                : 0L;
        return toDto(client, branchCount);
    }

    private ClientDto toDto(Client client, long branchCount) {
        String salesPersonName = null;
        if (client.getSalesUserId() != null) {
            salesPersonName = appUserRepository.findById(client.getSalesUserId())
                    .map(AppUser::getName)
                    .orElse(null);
        }
        return ClientDto.from(client, onboardingService.get(client.getId()), salesPersonName, branchCount);
    }

    private static String parseSalesUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim().toUpperCase();
        if (!code.matches("[A-Z0-9]{6}")) {
            throw new BadRequestException("Choose an active sales person");
        }
        return code;
    }
}
