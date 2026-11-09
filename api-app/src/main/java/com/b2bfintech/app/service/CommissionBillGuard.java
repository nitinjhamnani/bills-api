package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** A vendor who still has a bill waiting cannot be paused, removed, or have their rule deactivated. */
@Service
public class CommissionBillGuard {

    private final BillPaymentRepository billPaymentRepository;

    public CommissionBillGuard(BillPaymentRepository billPaymentRepository) {
        this.billPaymentRepository = billPaymentRepository;
    }

    public void assertRuleClear(UUID ruleId) {
        long waiting = billPaymentRepository.countByCommissionRuleIdAndOrgStatus(ruleId, BillPayment.OrgStatus.PENDING);
        if (waiting > 0) {
            throw new BadRequestException(waiting == 1
                    ? "A bill is still waiting with a vendor on this rule. Mark it paid or send it back first."
                    : waiting + " bills are still waiting with vendors on this rule. Mark them paid or send them back first.");
        }
    }

    public void assertVendorClear(UUID ruleId, UUID vendorClientId) {
        long waiting = billPaymentRepository.countByCommissionRuleIdAndAssignedVendorClientIdAndOrgStatus(
                ruleId, vendorClientId, BillPayment.OrgStatus.PENDING);
        if (waiting > 0) {
            throw new BadRequestException(waiting == 1
                    ? "This vendor still has a bill waiting. Mark it paid or send it back first."
                    : "This vendor still has " + waiting + " bills waiting. Mark them paid or send them back first.");
        }
    }
}
