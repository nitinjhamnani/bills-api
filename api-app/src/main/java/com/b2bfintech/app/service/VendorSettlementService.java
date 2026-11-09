package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.entity.CommissionCycle;
import com.b2bfintech.billing.entity.VendorReimbursement;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.CommissionCycleRepository;
import com.b2bfintech.billing.repository.VendorReimbursementRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.organisation.service.ClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checker side of the vendor cycle. A fetch that still shows the same due bill, or a fetch that
 * fails, stays in the queue. Reimbursement is the full bill amount. Commission is a later receipt.
 */
@Service
public class VendorSettlementService {

    private static final ZoneId PERIOD_ZONE = ZoneId.of("Asia/Kolkata");

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final VendorReimbursementRepository reimbursementRepository;
    private final CommissionCycleRepository commissionCycleRepository;
    private final BillFetchResolutionService fetchResolutionService;
    private final BillActivityLogService activityLogService;
    private final ClientService clientService;

    public VendorSettlementService(
            BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            VendorReimbursementRepository reimbursementRepository, CommissionCycleRepository commissionCycleRepository,
            BillFetchResolutionService fetchResolutionService, BillActivityLogService activityLogService,
            ClientService clientService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.commissionCycleRepository = commissionCycleRepository;
        this.fetchResolutionService = fetchResolutionService;
        this.activityLogService = activityLogService;
        this.clientService = clientService;
    }

    public List<BillPayment> awaitingVerification() {
        return billPaymentRepository.findByVendorStageOrderByVendorMarkedPaidAtAsc(BillPayment.VendorStage.VENDOR_PAID);
    }

    public List<BillPayment> awaitingReimbursement() {
        return billPaymentRepository.findByVendorStageOrderByBbpsVerifiedAtAsc(BillPayment.VendorStage.VERIFIED);
    }

    @Transactional
    public VerifyOutcome verifyOne(UUID paymentId, String actor) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (payment.getVendorStage() != BillPayment.VendorStage.VENDOR_PAID) {
            throw new BadRequestException("This bill is not waiting for a BBPS check");
        }
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        try {
            BillingProvider.FetchedBill fetched = fetchResolutionService.fetchCurrent(bill);
            if (cleared(payment, fetched)) {
                return markVerified(payment, actor, "The provider no longer shows this due bill.");
            }
            return keep(payment, actor, "The same bill is still due.");
        } catch (BillFetchFailedException ex) {
            if (noDueBill(ex)) {
                return markVerified(payment, actor, "The provider returned no due bill.");
            }
            return keep(payment, actor, ex.getMessage());
        } catch (RuntimeException ex) {
            return keep(payment, actor, ex.getMessage());
        }
    }

    @Transactional
    public VendorReimbursement reimburse(UUID vendorClientId, List<UUID> paymentIds, String reference, String actor) {
        String trimmed = requireReference(reference);
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        List<BillPayment> payments = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (UUID paymentId : new LinkedHashSet<>(paymentIds)) {
            BillPayment payment = billPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            if (payment.getVendorStage() != BillPayment.VendorStage.VERIFIED
                    || payment.getReimbursementId() != null
                    || payment.getAssignedVendorClientId() == null
                    || !payment.getAssignedVendorClientId().equals(vendorClientId)) {
                throw new BadRequestException("Only verified bills for this vendor can be reimbursed");
            }
            payments.add(payment);
            total = total.add(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
        }
        Instant now = Instant.now();
        VendorReimbursement reimbursement = new VendorReimbursement();
        reimbursement.setVendorClientId(vendorClientId);
        reimbursement.setTotalAmount(total);
        reimbursement.setReference(trimmed);
        reimbursement.setPaidAt(now);
        reimbursement.setActor(actor);
        reimbursement = reimbursementRepository.save(reimbursement);
        for (BillPayment payment : payments) {
            payment.setVendorStage(BillPayment.VendorStage.REIMBURSED);
            payment.setReimbursedAt(now);
            payment.setReimbursementId(reimbursement.getId());
            payment.setOrgStatus(BillPayment.OrgStatus.PAID);
            payment.setOrgPaidAt(now);
            payment.setOrgPaidBy(actor);
            billPaymentRepository.save(payment);
            activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_REIMBURSED,
                    "Reimbursed the vendor the full bill amount, reference " + trimmed, actor);
        }
        return reimbursement;
    }

    @Transactional(readOnly = true)
    public CommissionPreview preview(UUID vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        List<BillPayment> payments = billsInPeriod(vendorClientId, periodStart, periodEnd);
        if (payments.isEmpty()) {
            throw new BadRequestException("No reimbursed bills in this period are waiting for a commission cycle");
        }
        String vendorName = clientService.getById(vendorClientId).getClientName();
        Map<UUID, List<BillPayment>> byClient = new LinkedHashMap<>();
        for (BillPayment payment : payments) {
            byClient.computeIfAbsent(payment.getClientId(), id -> new ArrayList<>()).add(payment);
        }
        List<ClientGroup> groups = new ArrayList<>();
        BigDecimal grandAmount = BigDecimal.ZERO;
        BigDecimal grandCommission = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<BillPayment>> entry : byClient.entrySet()) {
            String clientName = clientService.getById(entry.getKey()).getClientName();
            List<BillLine> lines = new ArrayList<>();
            BigDecimal clientAmount = BigDecimal.ZERO;
            BigDecimal clientCommission = BigDecimal.ZERO;
            for (BillPayment payment : entry.getValue()) {
                Bill bill = billRepository.findById(payment.getBillId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
                BigDecimal commission = payment.getCommissionAmount() == null ? BigDecimal.ZERO : payment.getCommissionAmount();
                lines.add(new BillLine(
                        payment.getId(), bill.getCustomerBillAccountNumber(),
                        bill.getBillerName() == null ? bill.getBillServiceCode() : bill.getBillerName(),
                        payment.getAmount(), payment.getCommissionPercent(), commission));
                clientAmount = clientAmount.add(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
                clientCommission = clientCommission.add(commission);
            }
            groups.add(new ClientGroup(entry.getKey(), clientName, lines, clientAmount, clientCommission));
            grandAmount = grandAmount.add(clientAmount);
            grandCommission = grandCommission.add(clientCommission);
        }
        groups.sort(Comparator.comparing(ClientGroup::clientName, String.CASE_INSENSITIVE_ORDER));
        return new CommissionPreview(
                vendorClientId, vendorName, periodStart, periodEnd, groups, grandAmount, grandCommission, payments.size());
    }

    @Transactional
    public CommissionCycle close(UUID vendorClientId, LocalDate periodStart, LocalDate periodEnd, String reference, String actor) {
        String trimmed = requireReference(reference);
        List<BillPayment> payments = billsInPeriod(vendorClientId, periodStart, periodEnd);
        if (payments.isEmpty()) {
            throw new BadRequestException("No reimbursed bills in this period are waiting for a commission cycle");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        for (BillPayment payment : payments) {
            totalAmount = totalAmount.add(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
            totalCommission = totalCommission.add(payment.getCommissionAmount() == null ? BigDecimal.ZERO : payment.getCommissionAmount());
        }
        CommissionCycle cycle = new CommissionCycle();
        cycle.setVendorClientId(vendorClientId);
        cycle.setPeriodStart(periodStart);
        cycle.setPeriodEnd(periodEnd);
        cycle.setTotalAmount(totalAmount);
        cycle.setTotalCommission(totalCommission);
        cycle.setStatus(CommissionCycle.Status.CLOSED);
        cycle.setReceiptReference(trimmed);
        cycle.setClosedAt(Instant.now());
        cycle.setActor(actor);
        cycle = commissionCycleRepository.save(cycle);
        for (BillPayment payment : payments) {
            payment.setCommissionCycleId(cycle.getId());
            billPaymentRepository.save(payment);
            activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.COMMISSION_CLOSED,
                    "Commission received, reference " + trimmed, actor);
        }
        return cycle;
    }

    /** Recorded payments to vendors. Dates filter the reimbursement date. Leave both empty for every payment. */
    @Transactional(readOnly = true)
    public List<PaidPayment> paid(UUID vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        boolean hasStart = periodStart != null;
        boolean hasEnd = periodEnd != null;
        if (hasStart != hasEnd) {
            throw new BadRequestException("Choose both dates, or leave both empty");
        }
        if (hasStart && periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("The period end is before the start");
        }
        List<VendorReimbursement> reimbursements;
        if (hasStart) {
            Instant from = periodStart.atStartOfDay(PERIOD_ZONE).toInstant();
            Instant to = periodEnd.plusDays(1).atStartOfDay(PERIOD_ZONE).toInstant();
            reimbursements = vendorClientId == null
                    ? reimbursementRepository.findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(from, to)
                    : reimbursementRepository.findByVendorClientIdAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                            vendorClientId, from, to);
        } else if (vendorClientId == null) {
            reimbursements = reimbursementRepository.findAllByOrderByPaidAtDesc();
        } else {
            reimbursements = reimbursementRepository.findByVendorClientIdOrderByPaidAtDesc(vendorClientId);
        }
        if (reimbursements.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<BillPayment>> billsByReimbursement = new LinkedHashMap<>();
        for (BillPayment payment : billPaymentRepository.findByReimbursementIdIn(
                reimbursements.stream().map(VendorReimbursement::getId).toList())) {
            if (payment.getReimbursementId() == null) {
                continue;
            }
            billsByReimbursement.computeIfAbsent(payment.getReimbursementId(), id -> new ArrayList<>()).add(payment);
        }
        List<PaidPayment> result = new ArrayList<>();
        for (VendorReimbursement reimbursement : reimbursements) {
            String vendorName = clientService.getById(reimbursement.getVendorClientId()).getClientName();
            List<PaidBill> bills = new ArrayList<>();
            for (BillPayment payment : billsByReimbursement.getOrDefault(reimbursement.getId(), List.of())) {
                Bill bill = billRepository.findById(payment.getBillId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
                bills.add(new PaidBill(
                        payment.getId(),
                        bill.getCustomerBillAccountNumber(),
                        bill.getBillerName() == null ? bill.getBillServiceCode() : bill.getBillerName(),
                        payment.getAmount(),
                        payment.getCommissionCycleId() != null));
            }
            bills.sort(Comparator.comparing(PaidBill::consumerNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            result.add(new PaidPayment(
                    reimbursement.getId(), reimbursement.getVendorClientId(), vendorName,
                    reimbursement.getReference(), reimbursement.getPaidAt(), reimbursement.getTotalAmount(),
                    bills.size(), bills));
        }
        return result;
    }

    private List<BillPayment> billsInPeriod(UUID vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new BadRequestException("Choose a period");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("The period end is before the start");
        }
        Instant from = periodStart.atStartOfDay(PERIOD_ZONE).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(PERIOD_ZONE).toInstant();
        return billPaymentRepository.findForCommissionCycle(
                vendorClientId, BillPayment.VendorStage.REIMBURSED, from, to);
    }

    private VerifyOutcome markVerified(BillPayment payment, String actor, String note) {
        payment.setVendorStage(BillPayment.VendorStage.VERIFIED);
        payment.setBbpsVerifiedAt(Instant.now());
        payment.setBbpsCheckNote(null);
        billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.BBPS_VERIFIED, note, actor);
        return new VerifyOutcome(payment.getId(), true, note);
    }

    private VerifyOutcome keep(BillPayment payment, String actor, String note) {
        String stored = clip(note);
        payment.setBbpsCheckNote(stored);
        billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.BBPS_STILL_DUE, stored, actor);
        return new VerifyOutcome(payment.getId(), false, stored);
    }

    private static boolean cleared(BillPayment payment, BillingProvider.FetchedBill fetched) {
        if (fetched.amount() == null || fetched.amount().signum() <= 0) {
            return true;
        }
        return differs(payment.getBillNumber(), fetched.billNumber()) || differs(payment.getBillPeriod(), fetched.billPeriod());
    }

    private static boolean differs(String stored, String fetched) {
        if (stored == null || stored.isBlank() || fetched == null || fetched.isBlank()) {
            return false;
        }
        return !stored.trim().equalsIgnoreCase(fetched.trim());
    }

    private static boolean noDueBill(BillFetchFailedException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("no bill fields") || message.contains("no amount field");
    }

    private static String clip(String message) {
        if (message == null || message.isBlank()) {
            return "The bill fetch did not confirm payment.";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private static String requireReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BadRequestException("Enter the reference");
        }
        String trimmed = reference.trim();
        if (trimmed.length() > 120) {
            throw new BadRequestException("Reference must be 120 characters or less");
        }
        return trimmed;
    }

    public record VerifyOutcome(UUID billPaymentId, boolean verified, String note) {
    }

    public record PaidPayment(
            UUID id, UUID vendorClientId, String vendorName, String reference, Instant paidAt,
            BigDecimal totalAmount, int billCount, List<PaidBill> bills) {
    }

    public record PaidBill(
            UUID billPaymentId, String consumerNumber, String billerName, BigDecimal amount, boolean commissionReceived) {
    }

    public record CommissionPreview(
            UUID vendorClientId, String vendorName, LocalDate periodStart, LocalDate periodEnd,
            List<ClientGroup> clients, BigDecimal totalAmount, BigDecimal totalCommission, int billCount) {
    }

    public record ClientGroup(
            UUID clientId, String clientName, List<BillLine> bills, BigDecimal totalAmount, BigDecimal totalCommission) {
    }

    public record BillLine(
            UUID billPaymentId, String consumerNumber, String billerName,
            BigDecimal amount, BigDecimal commissionPercent, BigDecimal commissionAmount) {
    }
}
