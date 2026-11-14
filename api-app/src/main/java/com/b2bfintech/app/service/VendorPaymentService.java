package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.entity.BillPaymentRejection;
import com.b2bfintech.billing.repository.BillPaymentRejectionRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.document.DocumentStorageService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The vendor records that they paid the biller. Paytrix still marks the payment paid after checking it. */
@Service
public class VendorPaymentService {

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> PROOF_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp");

    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentRejectionRepository rejectionRepository;
    private final BillRepository billRepository;
    private final BillActivityLogService activityLogService;
    private final ClientAccountGuard clientAccountGuard;
    private final CommissionAssignmentService commissionAssignmentService;
    private final DocumentStorageService storageService;

    public VendorPaymentService(
            BillPaymentRepository billPaymentRepository, BillPaymentRejectionRepository rejectionRepository,
            BillRepository billRepository, BillActivityLogService activityLogService,
            ClientAccountGuard clientAccountGuard, CommissionAssignmentService commissionAssignmentService,
            DocumentStorageService storageService) {
        this.billPaymentRepository = billPaymentRepository;
        this.rejectionRepository = rejectionRepository;
        this.billRepository = billRepository;
        this.activityLogService = activityLogService;
        this.clientAccountGuard = clientAccountGuard;
        this.commissionAssignmentService = commissionAssignmentService;
        this.storageService = storageService;
    }

    public List<BillPayment> assigned(String vendorClientId) {
        return billPaymentRepository.findByAssignedVendorClientIdAndOrgStatusInOrderByAssignedAtDesc(
                vendorClientId,
                List.of(BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS, BillPayment.OrgStatus.PAID));
    }

    /** Bills the spreadsheet may change: still with this vendor, and chosen on the screen. */
    public List<BillPayment> sheetBills(String vendorClientId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        Map<UUID, BillPayment> eligible = assigned(vendorClientId).stream()
                .filter(VendorPaymentService::stillWithVendor)
                .collect(Collectors.toMap(BillPayment::getId, payment -> payment, (left, right) -> left, LinkedHashMap::new));
        List<BillPayment> chosen = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            BillPayment payment = eligible.get(id);
            if (payment == null) {
                throw new BadRequestException("Choose bills that are still with you");
            }
            chosen.add(payment);
        }
        return chosen;
    }

    private static boolean stillWithVendor(BillPayment payment) {
        BillPayment.VendorStage stage = payment.getVendorStage();
        return stage == null || stage == BillPayment.VendorStage.ASSIGNED
                || stage == BillPayment.VendorStage.IN_PROGRESS;
    }

    @Transactional
    public BillPayment markInProgress(String vendorClientId, UUID paymentId, String actor) {
        BillPayment payment = heldBy(vendorClientId, paymentId);
        if (stage(payment) != BillPayment.VendorStage.ASSIGNED || payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            throw new BadRequestException("This bill is not waiting to be marked in progress");
        }
        payment.setBillAssignedToStatus(BillPayment.AssignedStatus.IN_PROGRESS);
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_IN_PROGRESS,
                "Vendor marked this bill in progress.", actor);
        return payment;
    }

    /** The vendor paid the biller outside Paytrix. The proof and both references go to the checker. */
    @Transactional
    public BillPayment markPaid(String vendorClientId, UUID paymentId, String transactionReference,
                                String bbpsReference, MultipartFile proof, String actor) {
        clientAccountGuard.requireActiveVendor(vendorClientId);
        String transaction = requireReference(transactionReference, "Enter the transaction reference number");
        String bbps = requireReference(bbpsReference, "Enter the BBPS reference");
        requireProof(proof);
        BillPayment payment = heldBy(vendorClientId, paymentId);
        if (stage(payment) != BillPayment.VendorStage.IN_PROGRESS) {
            throw new BadRequestException(stage(payment) == BillPayment.VendorStage.ASSIGNED
                    ? "Mark this bill in progress before you mark it paid"
                    : "This bill has already moved on");
        }
        payment.setPaymentProof(storageService.store(proof, vendorClientId));
        payment.setTransactionReferenceNumber(transaction);
        payment.setBbpsExternalReference(bbps);
        payment.setBillPaidOn(Instant.now());
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_MARKED_PAID,
                "Vendor marked paid, transaction " + transaction + ", BBPS " + bbps, actor);
        MoneyActionLog.record("mark_paid", actor, payment.getId().toString());
        return payment;
    }

    private static void requireProof(MultipartFile proof) {
        if (proof == null || proof.isEmpty()) {
            throw new BadRequestException("Attach the proof of payment");
        }
        if (proof.getSize() > MAX_PROOF_BYTES) {
            throw new BadRequestException("The proof must be 5 MB or smaller");
        }
        String type = proof.getContentType() == null ? "" : proof.getContentType().toLowerCase();
        if (!PROOF_TYPES.contains(type)) {
            throw new BadRequestException("The proof must be a PDF, PNG, JPG, or WEBP file");
        }
    }

    /**
     * Classifies one spreadsheet row without writing it. {@code action} is IN_PROGRESS, PAID, or null
     * when the row should be left as it is. {@code error} is set when the row cannot be applied.
     */
    public SheetDecision decide(String vendorClientId, String paymentIdRaw, String statusRaw, String reference,
                                Set<UUID> selectedIds) {
        if (paymentIdRaw == null || paymentIdRaw.isBlank()) {
            return new SheetDecision(null, "Payment id is missing");
        }
        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return new SheetDecision(null, "Payment id is not valid");
        }
        if (selectedIds == null || !selectedIds.contains(paymentId)) {
            return new SheetDecision(null, "This bill was not selected");
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
            return new SheetDecision(null, "Mark this bill paid from the screen so you can attach the proof");
        }
        return new SheetDecision(null, "New status must be In progress");
    }

    public record SheetDecision(String action, String error) {
    }

    /**
     * Sends a still-pending assignment back. Status columns stay as they are. The vendor is
     * recorded so the next match cannot return the same payment to them.
     */
    @Transactional
    public BillPayment reject(String vendorClientId, UUID paymentId, String reason, String actor) {
        return rejectMany(vendorClientId, List.of(paymentId), reason, actor).get(0);
    }

    /** One reason is stored on every selected bill. Nothing is sent back unless every bill can be. */
    @Transactional
    public List<BillPayment> rejectMany(String vendorClientId, List<UUID> paymentIds, String reason, String actor) {
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

    private BillPayment sendBack(String vendorClientId, BillPayment payment, String trimmed, String actor) {
        BillPaymentRejection rejection = new BillPaymentRejection();
        rejection.setBillPaymentId(payment.getId());
        rejection.setVendorClientId(vendorClientId);
        rejection.setReason(trimmed);
        rejection.setRejectedBy(actor);
        rejectionRepository.save(rejection);

        payment.assignToPaytrix(Instant.now());
        payment = billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_REJECTED,
                "Sent back to Paytrix. " + trimmed, actor);

        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        Set<String> excluded = rejectionRepository.findByBillPaymentId(payment.getId()).stream()
                .map(BillPaymentRejection::getVendorClientId)
                .collect(Collectors.toSet());
        commissionAssignmentService.assignIfMatched(payment, bill, actor, excluded);
        return payment;
    }

    private BillPayment heldBy(String vendorClientId, UUID paymentId) {
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

    private static String requireReference(String reference, String missingMessage) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException(missingMessage);
        }
        String trimmed = reference.trim();
        if (trimmed.length() > 120) {
            throw new BadRequestException("A reference must be 120 characters or less");
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
