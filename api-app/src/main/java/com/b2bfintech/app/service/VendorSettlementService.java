package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.entity.VendorPayment;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.VendorPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.repository.VendorDetailsRepository;
import com.b2bfintech.wallet.entity.VendorTransaction;
import com.b2bfintech.wallet.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checker side of the vendor cycle. Verifying a vendor-paid bill records what Paytrix owes the
 * vendor in vendor_payments. Paytrix then pays the vendor, and later collects the commission.
 */
@Service
public class VendorSettlementService {

    private static final ZoneId PERIOD_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<String> PAYMENT_MODES = List.of("NEFT", "RTGS", "IMPS", "UPI", "CHEQUE", "CASH", "OTHER");

    private final BillPaymentRepository billPaymentRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillRepository billRepository;
    private final BillFetchResolutionService fetchResolutionService;
    private final BillActivityLogService activityLogService;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final CommissionAssignmentService commissionAssignmentService;
    private final ObjectMapper objectMapper;
    private final WalletService walletService;
    private final VendorDetailsRepository vendorDetailsRepository;

    public VendorSettlementService(
            BillPaymentRepository billPaymentRepository, BillFetchRepository billFetchRepository,
            BillRepository billRepository, BillFetchResolutionService fetchResolutionService,
            BillActivityLogService activityLogService, VendorPaymentRepository vendorPaymentRepository,
            CommissionAssignmentService commissionAssignmentService, ObjectMapper objectMapper,
            WalletService walletService, VendorDetailsRepository vendorDetailsRepository) {
        this.billPaymentRepository = billPaymentRepository;
        this.billFetchRepository = billFetchRepository;
        this.billRepository = billRepository;
        this.fetchResolutionService = fetchResolutionService;
        this.activityLogService = activityLogService;
        this.vendorPaymentRepository = vendorPaymentRepository;
        this.commissionAssignmentService = commissionAssignmentService;
        this.objectMapper = objectMapper;
        this.walletService = walletService;
        this.vendorDetailsRepository = vendorDetailsRepository;
    }

    public List<BillPayment> awaitingVerification() {
        return billPaymentRepository.findByVendorStageOrderByVendorMarkedPaidAtAsc(BillPayment.VendorStage.VENDOR_PAID);
    }

    /** Verified vendor bills that Paytrix has not paid the vendor for yet. */
    public List<BillPayment> awaitingReimbursement() {
        Set<UUID> unpaid = vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PENDING).stream()
                .map(VendorPayment::getBillPaymentId)
                .collect(Collectors.toSet());
        return billPaymentRepository.findByVendorStageOrderByBbpsVerifiedAtAsc(BillPayment.VendorStage.VERIFIED).stream()
                .filter(payment -> unpaid.contains(payment.getId()))
                .toList();
    }

    /** The checker's decision. It does not depend on a BBPS check having been run. */
    @Transactional
    public VerifyOutcome verifyOne(UUID paymentId, AuthenticatedUser actor) {
        BillPayment payment = awaitingCheck(paymentId);
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        Instant now = Instant.now();
        payment.setVerified(true);
        payment.setVerifiedOn(now);
        payment.setVerifiedById(actor.userId());
        payment.setVerifiedBy(actor.displayName());
        billPaymentRepository.save(payment);

        BillFetch fetch = payment.getBillFetch();
        fetch.setBillStatus(BillFetch.BillStatus.PAID);
        billFetchRepository.save(fetch);

        VendorPayment vendorPayment = new VendorPayment();
        vendorPayment.setBillPaymentId(payment.getId());
        vendorPayment.setBillAmount(payment.getBillAmount());
        vendorPayment.setBillPaidOn(payment.getBillPaidOn());
        vendorPayment.setCommissionPercent(commissionAssignmentService.currentPercent(payment, bill).orElse(null));
        vendorPaymentRepository.save(vendorPayment);

        String note = "Verified by " + actor.displayName() + ".";
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.BBPS_VERIFIED, note, actor.phoneNumber());
        MoneyActionLog.record("verify", actor.userId(), payment.getId().toString());
        return new VerifyOutcome(payment.getId(), true, note);
    }

    /** Fetches the bill from the provider so the checker can see whether anything is still due. Nothing is saved. */
    public BbpsCheck bbpsCheck(UUID paymentId, AuthenticatedUser actor) {
        BillPayment payment = awaitingCheck(paymentId);
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        BillFetchResolutionService.ProviderCheck check;
        try {
            check = fetchResolutionService.checkCurrent(bill);
        } catch (RuntimeException ex) {
            check = new BillFetchResolutionService.ProviderCheck(null, null, null, clip(ex.getMessage()));
        }
        boolean cleared = check.amount() != null && check.amount().signum() <= 0;
        String summary = check.error() != null ? check.error()
                : cleared ? "The provider shows Rs.0 pending."
                : check.amount() != null ? "The provider still shows Rs." + check.amount().toPlainString() + " pending."
                : "The provider response had no amount.";
        activityLogService.log(payment.getBillId(), payment.getId(),
                cleared ? BillPaymentLog.EventType.BBPS_VERIFIED : BillPaymentLog.EventType.BBPS_STILL_DUE,
                "BBPS check: " + clip(summary), actor.phoneNumber());
        return new BbpsCheck(payment.getId(), check.httpStatus(), check.amount(), cleared, summary, redact(check.body()));
    }

    private BillPayment awaitingCheck(UUID paymentId) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (payment.getVendorStage() != BillPayment.VendorStage.VENDOR_PAID) {
            throw new BadRequestException("This bill is not waiting for verification");
        }
        return payment;
    }

    /** Payment tokens in the provider response are hidden from the screen. */
    private String redact(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            hideTokens(root);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            return body.length() <= 4000 ? body : body.substring(0, 4000);
        }
    }

    private static void hideTokens(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String lower = name.toLowerCase();
                if (lower.contains("base64") || lower.contains("token")) {
                    object.put(name, "[hidden]");
                } else {
                    hideTokens(object.get(name));
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(VendorSettlementService::hideTokens);
        }
    }

    /** Pays the vendor for verified bills, outside the app. Each bill gets a vendor CREDIT and a platform DEBIT. */
    @Transactional
    public PayoutResult reimburse(String vendorClientId, List<UUID> paymentIds, String reference, String mode,
                                  AuthenticatedUser actor) {
        String trimmed = requireReference(reference);
        String paymentMode = requireMode(mode);
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        Instant now = Instant.now();
        BigDecimal total = BigDecimal.ZERO;
        for (UUID paymentId : new LinkedHashSet<>(paymentIds)) {
            BillPayment payment = billPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            VendorPayment owed = vendorPaymentRepository.findByBillPaymentId(paymentId).orElse(null);
            if (owed == null || owed.getPaymentStatus() != VendorPayment.PaymentStatus.PENDING
                    || !vendorClientId.equals(payment.getAssignedVendorClientId())) {
                throw new BadRequestException("Only verified, unpaid bills for this vendor can be paid");
            }
            owed.setPaymentStatus(VendorPayment.PaymentStatus.PAID);
            owed.setPaymentMode(paymentMode);
            owed.setPaymentMadeOn(now);
            owed.setPaymentMadeBy(madeBy(actor));
            owed.setPaymentMadeById(actor.userId());
            owed.setPaymentReferenceId(trimmed);
            vendorPaymentRepository.save(owed);
            walletService.recordVendorTransaction(
                    vendorClientId, VendorTransaction.Category.BILL_PAID, owed.getBillAmount(), actor.userId());
            activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.VENDOR_REIMBURSED,
                    "Paid the vendor Rs." + owed.getBillAmount() + " by " + paymentMode + ", reference " + trimmed,
                    actor.phoneNumber());
            MoneyActionLog.record("reimburse", actor.userId(), payment.getId().toString());
            total = total.add(owed.getBillAmount());
        }
        return new PayoutResult(total, trimmed, new LinkedHashSet<>(paymentIds).size());
    }

    /** One payment reference can cover verified bills for more than one vendor. */
    @Transactional
    public List<PayoutResult> reimburseSelected(List<UUID> paymentIds, String reference, String mode, AuthenticatedUser actor) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        Map<String, List<UUID>> byVendor = new LinkedHashMap<>();
        for (UUID paymentId : new LinkedHashSet<>(paymentIds)) {
            BillPayment payment = billPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            if (!payment.isWithVendor() || payment.getAssignedVendorClientId() == null) {
                throw new BadRequestException("Only verified vendor bills can be paid");
            }
            byVendor.computeIfAbsent(payment.getAssignedVendorClientId(), id -> new ArrayList<>()).add(paymentId);
        }
        List<PayoutResult> results = new ArrayList<>();
        for (Map.Entry<String, List<UUID>> entry : byVendor.entrySet()) {
            results.add(reimburse(entry.getKey(), entry.getValue(), reference, mode, actor));
        }
        return results;
    }

    /** Paid vendor bills in the period whose commission is still open. Vendor is optional. */
    @Transactional(readOnly = true)
    public List<CommissionBill> commissionBills(String vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new BadRequestException("Choose a period");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("The period end is before the start");
        }
        Instant from = periodStart.atStartOfDay(PERIOD_ZONE).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(PERIOD_ZONE).toInstant();
        return load(vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PAID)).stream()
                .filter(row -> vendorClientId == null || vendorClientId.isBlank()
                        || vendorClientId.equals(row.payment().getBillAssignedToId()))
                .filter(row -> !row.vendorPayment().isCommissionReceived())
                .filter(row -> row.vendorPayment().getCommissionPercent() != null)
                .filter(row -> {
                    Instant paidOn = row.vendorPayment().getPaymentMadeOn();
                    return paidOn != null && !paidOn.isBefore(from) && paidOn.isBefore(to);
                })
                .sorted(Comparator.comparing(row -> row.vendorPayment().getPaymentMadeOn()))
                .map(row -> new CommissionBill(
                        row.payment().getId(),
                        row.payment().getBillAssignedToId(),
                        row.payment().getBillAssignedToName(),
                        row.payment().getCorpName(),
                        row.bill().getPrimaryParamValue(),
                        row.bill().getOperatorName(),
                        row.vendorPayment().getBillAmount(),
                        row.vendorPayment().getCommissionPercent(),
                        row.vendorPayment().commissionAmount(),
                        row.vendorPayment().getPaymentMadeOn()))
                .toList();
    }

    /** Marks commission received for the chosen bills. One wallet debit is recorded per vendor. */
    @Transactional
    public CommissionResult receiveSelected(List<UUID> billPaymentIds, String reference, String mode, AuthenticatedUser actor) {
        String trimmed = requireReference(reference);
        String commissionMode = requireMode(mode);
        if (billPaymentIds == null || billPaymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        Map<UUID, VendorPayment> owedByPayment = vendorPaymentRepository.findByBillPaymentIdIn(new LinkedHashSet<>(billPaymentIds))
                .stream()
                .collect(Collectors.toMap(VendorPayment::getBillPaymentId, Function.identity(), (left, right) -> left));
        Map<String, List<Owed>> byVendor = new LinkedHashMap<>();
        for (UUID paymentId : new LinkedHashSet<>(billPaymentIds)) {
            VendorPayment owed = owedByPayment.get(paymentId);
            BillPayment payment = billPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            if (owed == null || owed.getPaymentStatus() != VendorPayment.PaymentStatus.PAID
                    || owed.isCommissionReceived() || owed.getCommissionPercent() == null
                    || !payment.isWithVendor()) {
                throw new BadRequestException("Only paid bills that still owe commission can be marked received");
            }
            Bill bill = billRepository.findById(payment.getBillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
            byVendor.computeIfAbsent(payment.getBillAssignedToId(), id -> new ArrayList<>())
                    .add(new Owed(owed, payment, bill));
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        int count = 0;
        for (Map.Entry<String, List<Owed>> entry : byVendor.entrySet()) {
            BigDecimal vendorCommission = BigDecimal.ZERO;
            for (Owed row : entry.getValue()) {
                VendorPayment vendorPayment = row.vendorPayment();
                vendorPayment.setCommissionReceived(true);
                vendorPayment.setCommissionStatus(VendorPayment.CommissionStatus.RECEIVED);
                vendorPayment.setCommissionReferenceId(trimmed);
                vendorPayment.setCommissionMode(commissionMode);
                vendorPaymentRepository.save(vendorPayment);
                activityLogService.log(row.payment().getBillId(), row.payment().getId(), BillPaymentLog.EventType.COMMISSION_CLOSED,
                        "Commission Rs." + vendorPayment.commissionAmount() + " received by " + commissionMode + ", reference " + trimmed,
                        actor.phoneNumber());
                MoneyActionLog.record("commission_close", actor.userId(), row.payment().getId().toString());
                totalAmount = totalAmount.add(vendorPayment.getBillAmount());
                vendorCommission = vendorCommission.add(vendorPayment.commissionAmount());
                count++;
            }
            if (vendorCommission.signum() > 0) {
                walletService.recordVendorTransaction(
                        entry.getKey(), VendorTransaction.Category.COMMISSION, vendorCommission, actor.userId());
            }
            totalCommission = totalCommission.add(vendorCommission);
        }
        return new CommissionResult(totalAmount, totalCommission, trimmed, count);
    }

    /** Bills paid to this vendor in the period whose commission has not come back yet. */
    @Transactional(readOnly = true)
    public CommissionPreview preview(String vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        List<Owed> owed = commissionDue(vendorClientId, periodStart, periodEnd);
        if (owed.isEmpty()) {
            String vendorName = vendorDetailsRepository.findById(vendorClientId)
                    .map(vendor -> vendor.getName())
                    .orElse(vendorClientId);
            return new CommissionPreview(
                    vendorClientId, vendorName, periodStart, periodEnd, List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        String vendorName = owed.get(0).payment().getBillAssignedToName();
        Map<String, List<Owed>> byCorp = new LinkedHashMap<>();
        for (Owed row : owed) {
            byCorp.computeIfAbsent(row.payment().getCorpId(), id -> new ArrayList<>()).add(row);
        }
        List<ClientGroup> groups = new ArrayList<>();
        BigDecimal grandAmount = BigDecimal.ZERO;
        BigDecimal grandCommission = BigDecimal.ZERO;
        for (Map.Entry<String, List<Owed>> entry : byCorp.entrySet()) {
            List<BillLine> lines = new ArrayList<>();
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal commission = BigDecimal.ZERO;
            for (Owed row : entry.getValue()) {
                lines.add(new BillLine(row.payment().getId(), row.bill().getPrimaryParamValue(), row.bill().getOperatorName(),
                        row.vendorPayment().getBillAmount(), row.vendorPayment().getCommissionPercent(),
                        row.vendorPayment().commissionAmount()));
                amount = amount.add(row.vendorPayment().getBillAmount());
                commission = commission.add(row.vendorPayment().commissionAmount());
            }
            groups.add(new ClientGroup(entry.getKey(), entry.getValue().get(0).payment().getCorpName(), lines, amount, commission));
            grandAmount = grandAmount.add(amount);
            grandCommission = grandCommission.add(commission);
        }
        groups.sort(Comparator.comparing(ClientGroup::clientName, String.CASE_INSENSITIVE_ORDER));
        return new CommissionPreview(
                vendorClientId, vendorName, periodStart, periodEnd, groups, grandAmount, grandCommission, owed.size());
    }

    /** The vendor paid Paytrix the commission for the period. Records one vendor DEBIT and one platform CREDIT. */
    @Transactional
    public CommissionResult close(String vendorClientId, LocalDate periodStart, LocalDate periodEnd, String reference,
                                  String mode, AuthenticatedUser actor) {
        String trimmed = requireReference(reference);
        String commissionMode = requireMode(mode);
        List<Owed> owed = commissionDue(vendorClientId, periodStart, periodEnd);
        if (owed.isEmpty()) {
            throw new BadRequestException("No bills paid to this vendor in this period are waiting for commission");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        for (Owed row : owed) {
            VendorPayment vendorPayment = row.vendorPayment();
            vendorPayment.setCommissionReceived(true);
            vendorPayment.setCommissionStatus(VendorPayment.CommissionStatus.RECEIVED);
            vendorPayment.setCommissionReferenceId(trimmed);
            vendorPayment.setCommissionMode(commissionMode);
            vendorPaymentRepository.save(vendorPayment);
            activityLogService.log(row.payment().getBillId(), row.payment().getId(), BillPaymentLog.EventType.COMMISSION_CLOSED,
                    "Commission Rs." + vendorPayment.commissionAmount() + " received by " + commissionMode + ", reference " + trimmed,
                    actor.phoneNumber());
            MoneyActionLog.record("commission_close", actor.userId(), row.payment().getId().toString());
            totalAmount = totalAmount.add(vendorPayment.getBillAmount());
            totalCommission = totalCommission.add(vendorPayment.commissionAmount());
        }
        if (totalCommission.signum() > 0) {
            walletService.recordVendorTransaction(
                    vendorClientId, VendorTransaction.Category.COMMISSION, totalCommission, actor.userId());
        }
        return new CommissionResult(totalAmount, totalCommission, trimmed, owed.size());
    }

    /** Payments made to vendors, one row per payment reference. Dates filter the payment date. */
    @Transactional(readOnly = true)
    public List<PaidPayment> paid(String vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        boolean hasStart = periodStart != null;
        boolean hasEnd = periodEnd != null;
        if (hasStart != hasEnd) {
            throw new BadRequestException("Choose both dates, or leave both empty");
        }
        if (hasStart && periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("The period end is before the start");
        }
        Instant from = hasStart ? periodStart.atStartOfDay(PERIOD_ZONE).toInstant() : null;
        Instant to = hasEnd ? periodEnd.plusDays(1).atStartOfDay(PERIOD_ZONE).toInstant() : null;
        Map<String, List<Owed>> payouts = new LinkedHashMap<>();
        for (Owed row : load(vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PAID))) {
            String vendorId = row.payment().getBillAssignedToId();
            Instant paidOn = row.vendorPayment().getPaymentMadeOn();
            if (vendorClientId != null && !vendorClientId.equals(vendorId)) {
                continue;
            }
            if (from != null && (paidOn == null || paidOn.isBefore(from) || !paidOn.isBefore(to))) {
                continue;
            }
            payouts.computeIfAbsent(vendorId + "|" + row.vendorPayment().getPaymentReferenceId() + "|" + paidOn,
                    key -> new ArrayList<>()).add(row);
        }
        List<PaidPayment> result = new ArrayList<>();
        for (List<Owed> rows : payouts.values()) {
            Owed first = rows.get(0);
            List<PaidBill> bills = rows.stream()
                    .map(row -> new PaidBill(row.payment().getId(), row.bill().getPrimaryParamValue(),
                            row.bill().getOperatorName(), row.vendorPayment().getBillAmount(),
                            row.vendorPayment().isCommissionReceived()))
                    .sorted(Comparator.comparing(PaidBill::consumerNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            BigDecimal total = rows.stream().map(row -> row.vendorPayment().getBillAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new PaidPayment(first.vendorPayment().getId(), first.payment().getBillAssignedToId(),
                    first.payment().getBillAssignedToName(), first.vendorPayment().getPaymentReferenceId(),
                    first.vendorPayment().getPaymentMadeOn(), total, bills.size(), bills));
        }
        result.sort(Comparator.comparing(PaidPayment::paidAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private List<Owed> commissionDue(String vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new BadRequestException("Choose a period");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("The period end is before the start");
        }
        Instant from = periodStart.atStartOfDay(PERIOD_ZONE).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(PERIOD_ZONE).toInstant();
        return load(vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PAID)).stream()
                .filter(row -> vendorClientId.equals(row.payment().getBillAssignedToId()))
                .filter(row -> !row.vendorPayment().isCommissionReceived())
                .filter(row -> row.vendorPayment().getCommissionPercent() != null)
                .filter(row -> {
                    Instant paidOn = row.vendorPayment().getPaymentMadeOn();
                    return paidOn != null && !paidOn.isBefore(from) && paidOn.isBefore(to);
                })
                .sorted(Comparator.comparing(row -> row.vendorPayment().getPaymentMadeOn()))
                .toList();
    }

    /** Verified bills for this vendor: what Paytrix owes, and the commission still due or received. */
    @Transactional(readOnly = true)
    public List<VendorTransactionRow> transactions(String vendorClientId) {
        List<BillPayment> assigned = billPaymentRepository.findAssignedToVendor(vendorClientId);
        if (assigned.isEmpty()) {
            return List.of();
        }
        Map<UUID, BillPayment> payments = assigned.stream()
                .collect(Collectors.toMap(BillPayment::getId, Function.identity(), (left, right) -> left));
        Map<String, Bill> bills = billRepository.findAllById(
                        assigned.stream().map(BillPayment::getBillId).filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Bill::getId, Function.identity(), (left, right) -> left));
        List<VendorTransactionRow> rows = new ArrayList<>();
        for (VendorPayment owed : vendorPaymentRepository.findByBillPaymentIdIn(payments.keySet())) {
            BillPayment payment = payments.get(owed.getBillPaymentId());
            if (payment == null) {
                continue;
            }
            Bill bill = bills.get(payment.getBillId());
            rows.add(new VendorTransactionRow(
                    owed.getId(),
                    payment.getId(),
                    payment.getCorpName(),
                    bill == null ? null : bill.getOperatorName(),
                    bill == null ? null : bill.getPrimaryParamValue(),
                    owed.getBillAmount(),
                    owed.getBillPaidOn(),
                    owed.getCommissionPercent(),
                    owed.commissionAmount(),
                    owed.getCommissionStatus().name(),
                    owed.getCommissionReferenceId(),
                    owed.getCommissionMode(),
                    owed.getPaymentStatus().name(),
                    owed.getPaymentMode(),
                    owed.getPaymentMadeOn(),
                    owed.getPaymentReferenceId()));
        }
        rows.sort(Comparator.comparing(VendorTransactionRow::billPaidOn, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private List<Owed> load(List<VendorPayment> vendorPayments) {
        Map<UUID, BillPayment> payments = billPaymentRepository.findAllById(
                        vendorPayments.stream().map(VendorPayment::getBillPaymentId).toList()).stream()
                .collect(Collectors.toMap(BillPayment::getId, Function.identity()));
        List<Owed> rows = new ArrayList<>();
        for (VendorPayment vendorPayment : vendorPayments) {
            BillPayment payment = payments.get(vendorPayment.getBillPaymentId());
            if (payment == null) {
                continue;
            }
            billRepository.findById(payment.getBillId())
                    .ifPresent(bill -> rows.add(new Owed(vendorPayment, payment, bill)));
        }
        return rows;
    }

    private static VendorPayment.PaymentMadeBy madeBy(AuthenticatedUser actor) {
        return switch (actor.role()) {
            case "CHECKER" -> VendorPayment.PaymentMadeBy.CHECKER;
            case "MAKER" -> VendorPayment.PaymentMadeBy.MAKER;
            default -> VendorPayment.PaymentMadeBy.ADMIN;
        };
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

    private static String requireMode(String mode) {
        String value = mode == null || mode.isBlank() ? "NEFT" : mode.trim().toUpperCase();
        if (!PAYMENT_MODES.contains(value)) {
            throw new BadRequestException("Mode must be one of " + String.join(", ", PAYMENT_MODES));
        }
        return value;
    }

    private record Owed(VendorPayment vendorPayment, BillPayment payment, Bill bill) {
    }

    private static String clip(String message) {
        if (message == null || message.isBlank()) {
            return "The bill fetch did not confirm payment.";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    public record VerifyOutcome(UUID billPaymentId, boolean verified, String note) {
    }

    public record PayoutResult(BigDecimal totalAmount, String reference, int billCount) {
    }

    public record CommissionResult(BigDecimal totalAmount, BigDecimal totalCommission, String reference, int billCount) {
    }

    /** {@code response} is the provider body with payment tokens hidden. */
    public record BbpsCheck(
            UUID billPaymentId, Integer httpStatus, BigDecimal amountDue, boolean zeroPending, String summary, String response) {
    }

    public record PaidPayment(
            UUID id, String vendorClientId, String vendorName, String reference, Instant paidAt,
            BigDecimal totalAmount, int billCount, List<PaidBill> bills) {
    }

    public record PaidBill(
            UUID billPaymentId, String consumerNumber, String billerName, BigDecimal amount, boolean commissionReceived) {
    }

    public record CommissionPreview(
            String vendorClientId, String vendorName, LocalDate periodStart, LocalDate periodEnd,
            List<ClientGroup> clients, BigDecimal totalAmount, BigDecimal totalCommission, int billCount) {
    }

    public record ClientGroup(
            String clientId, String clientName, List<BillLine> bills, BigDecimal totalAmount, BigDecimal totalCommission) {
    }

    public record BillLine(
            UUID billPaymentId, String consumerNumber, String billerName,
            BigDecimal amount, BigDecimal commissionPercent, BigDecimal commissionAmount) {
    }

    public record CommissionBill(
            UUID billPaymentId, String vendorClientId, String vendorName, String corporateName,
            String consumerNumber, String billerName, BigDecimal amount, BigDecimal commissionPercent,
            BigDecimal commissionAmount, Instant paidAt) {
    }

    /** One verified bill. Paytrix pays {@code billAmount} back to the vendor. The vendor pays {@code commissionAmount} to Paytrix. */
    public record VendorTransactionRow(
            UUID id, UUID billPaymentId, String corporateName, String billerName, String consumerNumber,
            BigDecimal billAmount, Instant billPaidOn, BigDecimal commissionPercent, BigDecimal commissionAmount,
            String commissionStatus, String commissionReference, String commissionMode,
            String paymentStatus, String paymentMode, Instant paymentMadeOn, String paymentReference) {
    }
}
