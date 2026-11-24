package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentLogRepository;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.ApprovalRule;
import com.b2bfintech.organisation.service.ApprovalRuleService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Enforces the organisation approval-rule thresholds on bill settlement and wallet top-up
 * review. {@code requiredApproverCount == 0} means no four-eyes check. A count of 1 or more
 * requires a distinct checker (or an organisation admin). No extra bill org-status is used —
 * {@code IN_PROCESS} is the wait for the checker.
 */
@Service
public class MakerCheckerPolicy {

    private static final Set<String> MONEY_ROLES = Set.of("SUPER_ADMIN", "ORGANISATION_ADMIN", "MAKER", "CHECKER");

    private final ApprovalRuleService approvalRuleService;
    private final BillPaymentLogRepository logRepository;

    public MakerCheckerPolicy(ApprovalRuleService approvalRuleService, BillPaymentLogRepository logRepository) {
        this.approvalRuleService = approvalRuleService;
        this.logRepository = logRepository;
    }

    /** Who last picked this bill up, from the activity log. */
    private String pickedUpBy(java.util.UUID paymentId) {
        return logRepository.findByBillPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .filter(entry -> entry.getEventType() == BillPaymentLog.EventType.ORG_PICKED_UP)
                .map(BillPaymentLog::getActor)
                .findFirst()
                .orElse(null);
    }

    public boolean requiresApproval(ApprovalRule.ActionType actionType, BigDecimal amount) {
        return approvalRuleService.requiresApproval(actionType, amount == null ? BigDecimal.ZERO : amount);
    }

    public boolean awaitingChecker(BillPayment payment) {
        if (payment.getAssignedVendorClientId() != null) {
            return false;
        }
        return payment.getOrgStatus() == BillPayment.OrgStatus.IN_PROCESS
                && requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, payment.getAmount());
    }

    public boolean awaitingChecker(com.b2bfintech.billing.repository.BillPaymentRow row) {
        if (row.isWithVendor()) {
            return false;
        }
        return row.orgStatus() == BillPayment.OrgStatus.IN_PROCESS
                && requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, row.billAmount());
    }

    public boolean canSettleBill(AuthenticatedUser user, com.b2bfintech.billing.repository.BillPaymentRow row) {
        if (user == null || row.isWithVendor() || row.orgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            return false;
        }
        if (!MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, row.billAmount())) {
            return true;
        }
        if ("SUPER_ADMIN".equals(user.role()) || "ORGANISATION_ADMIN".equals(user.role())) {
            return true;
        }
        if (!"CHECKER".equals(user.role())) {
            return false;
        }
        String picker = pickedUpBy(row.id());
        return !isSameActor(user, picker);
    }

    public boolean canSettleBill(AuthenticatedUser user, BillPayment payment) {
        if (user == null || payment.getAssignedVendorClientId() != null
                || payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            return false;
        }
        if (!MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, payment.getAmount())) {
            return true;
        }
        if ("SUPER_ADMIN".equals(user.role()) || "ORGANISATION_ADMIN".equals(user.role())) {
            return true;
        }
        if (!"CHECKER".equals(user.role())) {
            return false;
        }
        String picker = pickedUpBy(payment.getId());
        return !isSameActor(user, picker);
    }

    public void assertCanSettleBill(AuthenticatedUser user, BillPayment payment) {
        if (canSettleBill(user, payment)) {
            return;
        }
        throwDenied(user, pickedUpBy(payment.getId()), payment.getAmount());
    }

    /**
     * Direct Pay on a pending bill picks it up and settles it as the same person. A checker who
     * would become the picker cannot also settle a bill that needs approval. This is checked
     * before the provider is called.
     */
    public void assertCanDirectPay(AuthenticatedUser user, BillPayment payment) {
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PENDING) {
            String picker = user == null ? null : user.phoneNumber();
            if (canSettleAsPicker(user, payment.getAmount(), picker)) {
                return;
            }
            throwDenied(user, picker, payment.getAmount());
        }
        assertCanSettleBill(user, payment);
    }

    private boolean canSettleAsPicker(AuthenticatedUser user, BigDecimal amount, String picker) {
        if (user == null || !MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, amount)) {
            return true;
        }
        if ("SUPER_ADMIN".equals(user.role()) || "ORGANISATION_ADMIN".equals(user.role())) {
            return true;
        }
        return "CHECKER".equals(user.role()) && !isSameActor(user, picker);
    }

    private void throwDenied(AuthenticatedUser user, String picker, BigDecimal amount) {
        if (isSameActor(user, picker)
                && requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, amount)) {
            throw new AccessDeniedException("The person who picked up this bill cannot also settle it");
        }
        throw new AccessDeniedException("A checker must settle this bill because it is at or above the approval threshold");
    }

    private static boolean isSameActor(AuthenticatedUser user, String actor) {
        if (user == null || actor == null || actor.isBlank()) {
            return false;
        }
        return actor.equals(user.phoneNumber())
                || actor.equals(user.userId())
                || actor.equals(user.userCode());
    }

    public boolean canReviewTopup(AuthenticatedUser user, BigDecimal amount) {
        if (user == null || !MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.OFFLINE_TOPUP_CREDIT, amount)) {
            return true;
        }
        return "SUPER_ADMIN".equals(user.role()) || "ORGANISATION_ADMIN".equals(user.role()) || "CHECKER".equals(user.role());
    }

    public void assertCanReviewTopup(AuthenticatedUser user, BigDecimal amount) {
        if (!canReviewTopup(user, amount)) {
            throw new AccessDeniedException("A checker must review this top-up because it is at or above the approval threshold");
        }
    }
}
