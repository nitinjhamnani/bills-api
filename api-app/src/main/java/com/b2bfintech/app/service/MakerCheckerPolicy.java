package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPayment;
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

    private static final Set<String> MONEY_ROLES = Set.of("ORGANISATION_ADMIN", "MAKER", "CHECKER");

    private final ApprovalRuleService approvalRuleService;

    public MakerCheckerPolicy(ApprovalRuleService approvalRuleService) {
        this.approvalRuleService = approvalRuleService;
    }

    public boolean requiresApproval(ApprovalRule.ActionType actionType, BigDecimal amount) {
        return approvalRuleService.requiresApproval(actionType, amount == null ? BigDecimal.ZERO : amount);
    }

    public boolean awaitingChecker(BillPayment payment) {
        return payment.getOrgStatus() == BillPayment.OrgStatus.IN_PROCESS
                && requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, payment.getAmount());
    }

    public boolean canSettleBill(AuthenticatedUser user, BillPayment payment) {
        if (user == null || payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            return false;
        }
        if (!MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, payment.getAmount())) {
            return true;
        }
        if ("ORGANISATION_ADMIN".equals(user.role())) {
            return true;
        }
        if (!"CHECKER".equals(user.role())) {
            return false;
        }
        String picker = payment.getOrgPickedUpBy();
        return picker == null || !picker.equals(user.phoneNumber());
    }

    public void assertCanSettleBill(AuthenticatedUser user, BillPayment payment) {
        if (canSettleBill(user, payment)) {
            return;
        }
        if (user != null && user.phoneNumber() != null && user.phoneNumber().equals(payment.getOrgPickedUpBy())
                && requiresApproval(ApprovalRule.ActionType.BILL_PAYMENT, payment.getAmount())) {
            throw new AccessDeniedException("The person who picked up this bill cannot also settle it");
        }
        throw new AccessDeniedException("A checker must settle this bill because it is at or above the approval threshold");
    }

    public boolean canReviewTopup(AuthenticatedUser user, BigDecimal amount) {
        if (user == null || !MONEY_ROLES.contains(user.role())) {
            return false;
        }
        if (!requiresApproval(ApprovalRule.ActionType.OFFLINE_TOPUP_CREDIT, amount)) {
            return true;
        }
        return "ORGANISATION_ADMIN".equals(user.role()) || "CHECKER".equals(user.role());
    }

    public void assertCanReviewTopup(AuthenticatedUser user, BigDecimal amount) {
        if (!canReviewTopup(user, amount)) {
            throw new AccessDeniedException("A checker must review this top-up because it is at or above the approval threshold");
        }
    }
}
