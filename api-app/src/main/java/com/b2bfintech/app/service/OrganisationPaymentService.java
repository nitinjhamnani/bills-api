package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Paytrix settling a bill it holds: pick it up, then mark it paid with the references, or mark it
 * failed and send it back to the queue. Amounts at or above the BILL_PAYMENT approval threshold
 * need a distinct checker or an organisation admin to mark paid.
 */
@Service
public class OrganisationPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillActivityLogService activityLogService;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public OrganisationPaymentService(
            BillPaymentRepository billPaymentRepository, BillFetchRepository billFetchRepository,
            BillActivityLogService activityLogService, MakerCheckerPolicy makerCheckerPolicy) {
        this.billPaymentRepository = billPaymentRepository;
        this.billFetchRepository = billFetchRepository;
        this.activityLogService = activityLogService;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @Transactional
    public BillPayment pickUp(UUID id, String actor) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            throw new BadRequestException("Bill payment is not pending pick-up");
        }
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.IN_PROGRESS);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PICKED_UP,
                "Picked up by " + actor, actor);
        return payment;
    }

    /**
     * Excel import path: a row marked Paid/Failed is the settlement action. Pending rows are
     * picked up first so the workbook does not fail just because pick-up was not done in the UI.
     * Already paid rows are left as they are.
     */
    @Transactional
    public BillPayment settleFromImport(
            UUID id, AuthenticatedUser actor, boolean paid,
            String bbpsTransactionId, String externalTransactionId, String billType, String failureReason) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return payment;
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PENDING) {
            pickUp(id, actor.phoneNumber());
        }
        if (paid) {
            return markPaid(id, actor, bbpsTransactionId, externalTransactionId, billType);
        }
        return markFailed(id, actor.phoneNumber(), failureReason);
    }

    /** The bill must be picked up first, whether this is one click or an Excel row. */
    @Transactional
    public BillPayment markPaid(UUID id, AuthenticatedUser actor, String bbpsTransactionId, String externalTransactionId, String billType) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked paid");
        }
        makerCheckerPolicy.assertCanSettleBill(actor, payment);
        payment.setBillPaidOn(Instant.now());
        payment.setBbpsExternalReference(blankToNull(bbpsTransactionId));
        payment.setTransactionReferenceNumber(blankToNull(externalTransactionId));
        payment = billPaymentRepository.save(payment);

        BillFetch fetch = payment.getBillFetch();
        fetch.setBillStatus(BillFetch.BillStatus.PAID);
        fetch.setBillPaymentStatus(BillFetch.PaymentStatus.PAID);
        billFetchRepository.save(fetch);

        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PAID,
                "Marked paid by " + actor.phoneNumber(), actor.phoneNumber());
        MoneyActionLog.record("mark_paid", actor.userId(), payment.getId().toString());
        return payment;
    }

    /** Sends it back to the queue so Paytrix can pick it up and try again. The reason is logged. */
    @Transactional
    public BillPayment markFailed(UUID id, String actor, String reason) {
        BillPayment payment = getOrThrow(id);
        refuseAssigned(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked failed");
        }
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.PENDING);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_MARK_FAILED, reason, actor);
        return payment;
    }

    private static void refuseAssigned(BillPayment payment) {
        if (payment.isWithVendor()) {
            throw new BadRequestException("This bill is with a vendor. Verify it from Settlements.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BillPayment getOrThrow(UUID id) {
        return billPaymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
    }
}
