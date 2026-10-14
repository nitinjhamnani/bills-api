package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The organisation's side of settling a {@link BillPayment}: pick it up, then either mark it paid
 * (records how it was actually settled with the biller) or mark it failed (sent back to the queue
 * for a retry, with the reason recorded) - the simple {@code PENDING -> IN_PROCESS -> PAID} flow
 * the user specified. Amounts at or above the BILL_PAYMENT approval threshold need a
 * distinct checker (or an organisation admin) to mark paid; {@code IN_PROCESS} is that wait.
 */
@Service
public class OrganisationPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final BillActivityLogService activityLogService;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public OrganisationPaymentService(
            BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            BillActivityLogService activityLogService, MakerCheckerPolicy makerCheckerPolicy) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.activityLogService = activityLogService;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @Transactional
    public BillPayment pickUp(UUID id, String actor) {
        BillPayment payment = getOrThrow(id);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            throw new BadRequestException("Bill payment is not pending pick-up");
        }
        payment.setOrgStatus(BillPayment.OrgStatus.IN_PROCESS);
        payment.setOrgPickedUpAt(Instant.now());
        payment.setOrgPickedUpBy(actor);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PICKED_UP,
                "Picked up by " + actor, actor);
        return payment;
    }

    /**
     * Excel import path: a row marked Paid/Failed is the settlement action. PENDING rows are
     * picked up first so the workbook does not fail just because pick-up was not done in the UI.
     * NA (client has not paid) is rejected. Already-PAID rows are left as-is.
     */
    @Transactional
    public BillPayment settleFromImport(
            UUID id, AuthenticatedUser actor, boolean paid,
            String bbpsTransactionId, String externalTransactionId, String billType, String failureReason) {
        BillPayment payment = getOrThrow(id);
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return payment;
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.NA) {
            throw new BadRequestException("This bill is still waiting for the client to pay");
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PENDING) {
            pickUp(id, actor.phoneNumber());
        }
        if (paid) {
            return markPaid(id, actor, bbpsTransactionId, externalTransactionId, billType);
        }
        return markFailed(id, actor.phoneNumber(), failureReason);
    }

    /** Requires the bill payment to already be picked up (IN_PROCESS) - this is enforced the same
     * way whether the caller is a single "Mark paid" click or a bulk Excel upload row, so a bill
     * can never be marked paid without first passing through pick-up. */
    @Transactional
    public BillPayment markPaid(UUID id, AuthenticatedUser actor, String bbpsTransactionId, String externalTransactionId, String billType) {
        BillPayment payment = getOrThrow(id);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked paid");
        }
        makerCheckerPolicy.assertCanSettleBill(actor, payment);
        String actorPhone = actor.phoneNumber();
        payment.setOrgStatus(BillPayment.OrgStatus.PAID);
        payment.setBillStatus(Bill.BillStatus.PAID);
        payment.setOrgPaidAt(Instant.now());
        payment.setOrgPaidBy(actorPhone);
        payment.setBbpsTransactionId(bbpsTransactionId);
        payment.setExternalTransactionId(externalTransactionId);
        payment.setBillType(billType);
        payment = billPaymentRepository.save(payment);

        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        bill.setBillStatus(Bill.BillStatus.PAID);
        billRepository.save(bill);

        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_PAID,
                "Marked paid by " + actorPhone, actorPhone);
        return payment;
    }

    /** Sends it back to the queue (org_status PENDING) with the failure reason recorded, rather
     * than a dead end - the organisation can pick it up and retry once the underlying issue (biller
     * outage, wrong details, etc.) is resolved. */
    @Transactional
    public BillPayment markFailed(UUID id, String actor, String reason) {
        BillPayment payment = getOrThrow(id);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.IN_PROCESS) {
            throw new BadRequestException("Bill payment must be picked up before it can be marked failed");
        }
        payment.setOrgStatus(BillPayment.OrgStatus.PENDING);
        payment.setBillPayFailureReason(reason);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ORG_MARK_FAILED, reason, actor);
        return payment;
    }

    private BillPayment getOrThrow(UUID id) {
        return billPaymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
    }
}
