package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.entity.BillPaymentRejection;
import com.b2bfintech.billing.repository.BillPaymentRejectionRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The vendor records that they paid the biller. Paytrix still marks the payment paid after checking it. */
@Service
public class VendorPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentRejectionRepository rejectionRepository;
    private final BillRepository billRepository;
    private final BillActivityLogService activityLogService;
    private final ClientAccountGuard clientAccountGuard;
    private final CommissionAssignmentService commissionAssignmentService;

    public VendorPaymentService(
            BillPaymentRepository billPaymentRepository, BillPaymentRejectionRepository rejectionRepository,
            BillRepository billRepository, BillActivityLogService activityLogService,
            ClientAccountGuard clientAccountGuard, CommissionAssignmentService commissionAssignmentService) {
        this.billPaymentRepository = billPaymentRepository;
        this.rejectionRepository = rejectionRepository;
        this.billRepository = billRepository;
        this.activityLogService = activityLogService;
        this.clientAccountGuard = clientAccountGuard;
        this.commissionAssignmentService = commissionAssignmentService;
    }

    public List<BillPayment> assigned(UUID vendorClientId) {
        return billPaymentRepository.findByAssignedVendorClientIdAndOrgStatusInOrderByAssignedAtDesc(
                vendorClientId,
                List.of(BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS, BillPayment.OrgStatus.PAID));
    }

    public List<BillPayment> sheetBills(UUID vendorClientId) {
        return assigned(vendorClientId).stream()
                .filter(payment -> {
                    BillPayment.VendorStage stage = payment.getVendorStage();
                    return stage == null || stage == BillPayment.VendorStage.ASSIGNED
                            || stage == BillPayment.VendorStage.IN_PROGRESS;
                })
                .toList();
    }

    @Transactional
    public BillPayment markInProgress(UUID vendorClientId, UUID paymentId, String actor) {
        BillPayment payment = heldBy(vendorClientId, paymentId);
        if (stage(payment) != BillPayment.VendorStage.ASSIGNED || payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            throw new BadRequestException("This bill is not waiting to be marked in progress");
        }
        payment.setVendorStage(BillPayment.VendorStage.IN_PROGRESS);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_IN_PROGRESS,
                "Vendor marked this bill in progress.", actor);
        return payment;
    }

    @Transactional
    public BillPayment markPaid(UUID vendorClientId, UUID paymentId, String reference, String actor) {
        return markPaid(vendorClientId, paymentId, reference, actor, true);
    }

    /** A spreadsheet can jump an assigned bill straight to paid. The screen still requires in progress. */
    @Transactional
    public BillPayment markPaidFromSheet(UUID vendorClientId, UUID paymentId, String reference, String actor) {
        return markPaid(vendorClientId, paymentId, reference, actor, false);
    }

    private BillPayment markPaid(UUID vendorClientId, UUID paymentId, String reference, String actor, boolean requireInProgress) {
        clientAccountGuard.requireActiveVendor(vendorClientId);
        String trimmed = requireReference(reference);
        BillPayment payment = heldBy(vendorClientId, paymentId);
        BillPayment.VendorStage stage = stage(payment);
        if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING
                || (stage != BillPayment.VendorStage.ASSIGNED && stage != BillPayment.VendorStage.IN_PROGRESS)) {
            throw new BadRequestException("This bill has already moved on");
        }
        if (requireInProgress && stage != BillPayment.VendorStage.IN_PROGRESS) {
            throw new BadRequestException("Mark this bill in progress before you mark it paid");
        }
        BigDecimal percent = commissionAssignmentService.livePercent(payment)
                .orElseThrow(() -> new BadRequestException(
                        "Paytrix has to finish setup before you can mark this paid."));
        BigDecimal commission = payment.getAmount()
                .multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        payment.setCommissionPercent(percent);
        payment.setCommissionAmount(commission);
        payment.setVendorPaymentReference(trimmed);
        payment.setVendorMarkedPaidAt(Instant.now());
        payment.setVendorStage(BillPayment.VendorStage.VENDOR_PAID);
        payment.setBbpsCheckNote(null);
        payment.setOrgStatus(BillPayment.OrgStatus.IN_PROCESS);
        payment.setOrgPickedUpAt(Instant.now());
        payment.setOrgPickedUpBy(actor);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_MARKED_PAID,
                "Vendor marked paid at " + percent.toPlainString() + "%, reference " + trimmed, actor);
        return payment;
    }

    /**
     * Classifies one spreadsheet row without writing it. {@code action} is IN_PROGRESS, PAID, or null
     * when the row should be left as it is. {@code error} is set when the row cannot be applied.
     */
    public SheetDecision decide(UUID vendorClientId, String paymentIdRaw, String statusRaw, String reference) {
        if (paymentIdRaw == null || paymentIdRaw.isBlank()) {
            return new SheetDecision(null, "Payment id is missing");
        }
        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return new SheetDecision(null, "Payment id is not valid");
        }
        BillPayment payment = billPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getAssignedVendorClientId() == null
                || !payment.getAssignedVendorClientId().equals(vendorClientId)) {
            return new SheetDecision(null, "This bill is not on your list");
        }
        String status = statusRaw == null ? "" : statusRaw.trim().toLowerCase().replace('_', ' ');
        if (status.isEmpty() || status.equals("unchanged")) {
            return new SheetDecision(null, null);
        }
        BillPayment.VendorStage stage = stage(payment);
        if (status.equals("in progress") || status.equals("inprogress")) {
            if (stage == BillPayment.VendorStage.IN_PROGRESS) {
                return new SheetDecision(null, null);
            }
            if (stage != BillPayment.VendorStage.ASSIGNED) {
                return new SheetDecision(null, "This bill cannot move backwards");
            }
            return new SheetDecision("IN_PROGRESS", null);
        }
        if (status.equals("paid")) {
            if (stage != BillPayment.VendorStage.ASSIGNED && stage != BillPayment.VendorStage.IN_PROGRESS) {
                return new SheetDecision(null, "This bill cannot move backwards");
            }
            if (reference == null || reference.isBlank()) {
                return new SheetDecision(null, "A paid row needs a reference");
            }
            if (reference.trim().length() > 120) {
                return new SheetDecision(null, "Transaction reference must be 120 characters or less");
            }
            return new SheetDecision("PAID", null);
        }
        return new SheetDecision(null, "New status must be In progress or Paid");
    }

    public record SheetDecision(String action, String error) {
    }

    /**
     * Sends a still-pending assignment back. Status columns stay as they are. The vendor is
     * recorded so the next match cannot return the same payment to them.
     */
    @Transactional
    public BillPayment reject(UUID vendorClientId, UUID paymentId, String reason, String actor) {
        return rejectMany(vendorClientId, List.of(paymentId), reason, actor).get(0);
    }

    /** One reason is stored on every selected bill. Nothing is sent back unless every bill can be. */
    @Transactional
    public List<BillPayment> rejectMany(UUID vendorClientId, List<UUID> paymentIds, String reason, String actor) {
        clientAccountGuard.requireActiveVendor(vendorClientId);
        String trimmed = requireReason(reason);
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        List<BillPayment> payments = new ArrayList<>();
        for (UUID paymentId : new LinkedHashSet<>(paymentIds)) {
            BillPayment payment = billPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            if (payment.getAssignedVendorClientId() == null || !payment.getAssignedVendorClientId().equals(vendorClientId)) {
                throw new UnauthorizedException("One of these bills is not assigned to your account");
            }
            if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
                throw new BadRequestException("Send back only bills that are still waiting for you");
            }
            payments.add(payment);
        }
        List<BillPayment> sent = new ArrayList<>();
        for (BillPayment payment : payments) {
            sent.add(sendBack(vendorClientId, payment, trimmed, actor));
        }
        return sent;
    }

    private BillPayment sendBack(UUID vendorClientId, BillPayment payment, String trimmed, String actor) {
        BillPaymentRejection rejection = new BillPaymentRejection();
        rejection.setBillPaymentId(payment.getId());
        rejection.setVendorClientId(vendorClientId);
        rejection.setReason(trimmed);
        rejection.setRejectedBy(actor);
        rejectionRepository.save(rejection);

        payment.setAssignedVendorClientId(null);
        payment.setCommissionRuleId(null);
        payment.setCommissionPercent(null);
        payment.setCommissionAmount(null);
        payment.setAssignedAt(null);
        payment.setVendorStage(null);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_REJECTED,
                "Sent back to Paytrix. " + trimmed, actor);

        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        Set<UUID> excluded = rejectionRepository.findByBillPaymentId(payment.getId()).stream()
                .map(BillPaymentRejection::getVendorClientId)
                .collect(Collectors.toSet());
        commissionAssignmentService.assignIfMatched(payment, bill, actor, excluded);
        return payment;
    }

    private BillPayment heldBy(UUID vendorClientId, UUID paymentId) {
        clientAccountGuard.requireActiveVendor(vendorClientId);
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (payment.getAssignedVendorClientId() == null || !payment.getAssignedVendorClientId().equals(vendorClientId)) {
            throw new UnauthorizedException("This bill is not assigned to your account");
        }
        return payment;
    }

    private static BillPayment.VendorStage stage(BillPayment payment) {
        return payment.getVendorStage() == null ? BillPayment.VendorStage.ASSIGNED : payment.getVendorStage();
    }

    private static String requireReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("Enter the transaction reference");
        }
        String trimmed = reference.trim();
        if (trimmed.length() > 120) {
            throw new BadRequestException("Transaction reference must be 120 characters or less");
        }
        return trimmed;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Enter a reason for sending this bill back");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 300) {
            throw new BadRequestException("Reason must be 300 characters or less");
        }
        return trimmed;
    }
}
