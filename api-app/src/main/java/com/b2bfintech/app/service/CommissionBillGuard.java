package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Commission;
import org.springframework.stereotype.Service;

/**
 * A commission cannot be deactivated or deleted while its vendor still holds an unpaid bill on
 * that operator and amount range.
 */
@Service
public class CommissionBillGuard {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;

    public CommissionBillGuard(BillPaymentRepository billPaymentRepository, BillRepository billRepository) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
    }

    public void assertClear(Commission commission) {
        long waiting = billPaymentRepository.findAssignedToVendor(commission.getVendorId()).stream()
                .filter(payment -> payment.getOrgStatus() == BillPayment.OrgStatus.PENDING)
                .filter(payment -> commission.covers(payment.getBillAmount()))
                .filter(payment -> onOperator(payment, commission))
                .count();
        if (waiting > 0) {
            throw new BadRequestException(waiting == 1
                    ? commission.getVendorName() + " still has a bill waiting on this commission. Mark it paid or send it back first."
                    : commission.getVendorName() + " still has " + waiting
                            + " bills waiting on this commission. Mark them paid or send them back first.");
        }
    }

    private boolean onOperator(BillPayment payment, Commission commission) {
        Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
        return bill != null
                && commission.getCategoryId().equals(bill.getCategoryId())
                && commission.getOperatorName().equalsIgnoreCase(bill.getOperatorName());
    }
}
