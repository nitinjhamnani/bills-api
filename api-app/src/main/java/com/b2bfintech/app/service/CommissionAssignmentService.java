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
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.entity.Commission;
import com.b2bfintech.organisation.service.CommissionService;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * After a corporate pays, the vendor with the highest approved, active commission for the bill's
 * operator and amount takes the bill. Bills that match nothing stay with Paytrix. An organisation
 * admin can run the same match later for bills still waiting with Paytrix.
 */
@Service
public class CommissionAssignmentService {

    private final CommissionService commissionService;
    private final ClientService clientService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final BillPaymentRejectionRepository rejectionRepository;
    private final BillActivityLogService activityLogService;
    private final CategoryOperatorRepository operatorRepository;

    public CommissionAssignmentService(
            CommissionService commissionService, ClientService clientService,
            BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            BillPaymentRejectionRepository rejectionRepository,
            BillActivityLogService activityLogService, CategoryOperatorRepository operatorRepository) {
        this.commissionService = commissionService;
        this.clientService = clientService;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.rejectionRepository = rejectionRepository;
        this.activityLogService = activityLogService;
        this.operatorRepository = operatorRepository;
    }

    @Transactional
    public void assignIfMatched(BillPayment payment, Bill bill, String actor) {
        assignIfMatched(payment, bill, actor, Set.of());
    }

    /** {@code excludedVendorIds} are vendors who already sent this payment back. */
    @Transactional
    public void assignIfMatched(BillPayment payment, Bill bill, String actor, Set<String> excludedVendorIds) {
        if (payment.isWithVendor() || payment.getBillAmount() == null) {
            return;
        }
        coveringCommission(payment, bill, excludedVendorIds).ifPresent(match -> apply(payment, match, actor));
    }

    /** What a manual assign would do. Nothing is written. */
    @Transactional(readOnly = true)
    public List<AssignmentPlan> preview(List<UUID> paymentIds) {
        return decide(paymentIds);
    }

    /** Assigns the selected Paytrix bills that a live commission covers. The rest stay put. */
    @Transactional
    public List<AssignmentPlan> assign(List<UUID> paymentIds, String actor) {
        List<AssignmentPlan> plans = decide(paymentIds);
        List<AssignmentPlan> done = new ArrayList<>();
        for (AssignmentPlan plan : plans) {
            if (!plan.willAssign()) {
                done.add(plan);
                continue;
            }
            BillPayment payment = billPaymentRepository.findById(plan.billPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            Bill bill = billRepository.findById(payment.getBillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
            AssignmentPlan fresh = decideOne(payment, bill);
            if (!fresh.willAssign()) {
                done.add(fresh);
                continue;
            }
            Commission match = coveringCommission(payment, bill, excludedVendors(payment)).orElseThrow();
            apply(payment, match, actor);
            done.add(fresh);
        }
        return done;
    }

    /** The assigned vendor's best live commission that covers this bill and amount. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> currentPercent(BillPayment payment, Bill bill) {
        if (!payment.isWithVendor() || payment.getBillAmount() == null) {
            return Optional.empty();
        }
        return commissionService.liveRates(bill.getCategoryId(), operatorId(bill)).stream()
                .filter(option -> option.getVendorId().equals(payment.getBillAssignedToId()))
                .filter(option -> option.covers(payment.getBillAmount()))
                .map(Commission::getCommission)
                .max(Comparator.naturalOrder());
    }

    private List<AssignmentPlan> decide(List<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BadRequestException("Choose at least one bill");
        }
        List<AssignmentPlan> plans = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(paymentIds)) {
            BillPayment payment = billPaymentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
            Bill bill = billRepository.findById(payment.getBillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
            plans.add(decideOne(payment, bill));
        }
        return plans;
    }

    private AssignmentPlan decideOne(BillPayment payment, Bill bill) {
        String blocked = blockReason(payment);
        if (blocked != null) {
            return plan(payment, bill, false, null, blocked);
        }
        return coveringCommission(payment, bill, excludedVendors(payment))
                .map(match -> plan(payment, bill, true, match, null))
                .orElseGet(() -> plan(payment, bill, false, null, uncoveredReason(payment, bill)));
    }

    private String blockReason(BillPayment payment) {
        if (payment.isWithVendor()) {
            return "Already assigned to " + payment.getBillAssignedToName();
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.PAID) {
            return "Already settled";
        }
        if (payment.getOrgStatus() == BillPayment.OrgStatus.IN_PROCESS) {
            return "Paytrix has already picked this bill up";
        }
        if (payment.getBillAmount() == null) {
            return "This bill has no amount";
        }
        return null;
    }

    private String uncoveredReason(BillPayment payment, Bill bill) {
        Set<String> excluded = excludedVendors(payment);
        List<Commission> covering = commissionService.liveRates(bill.getCategoryId(), operatorId(bill)).stream()
                .filter(option -> option.covers(payment.getBillAmount()))
                .filter(this::vendorIsActive)
                .toList();
        if (!covering.isEmpty() && covering.stream().allMatch(option -> excluded.contains(option.getVendorId()))) {
            return "The vendor for this amount already sent the bill back";
        }
        return "No commission rule covers this amount";
    }

    private Optional<Commission> coveringCommission(BillPayment payment, Bill bill, Set<String> excludedVendorIds) {
        Set<String> excluded = excludedVendorIds == null ? Set.of() : excludedVendorIds;
        return commissionService.liveRates(bill.getCategoryId(), operatorId(bill)).stream()
                .filter(option -> !excluded.contains(option.getVendorId()))
                .filter(option -> option.covers(payment.getBillAmount()))
                .filter(this::vendorIsActive)
                .max(Comparator.comparing(Commission::getCommission)
                        .thenComparing(Commission::getCreatedAt, Comparator.reverseOrder()));
    }

    private void apply(BillPayment payment, Commission match, String actor) {
        Client vendor = clientService.getById(match.getVendorId());
        payment.assignToVendor(vendor.getId(), vendor.getClientName(), Instant.now());
        billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ASSIGNED,
                "Assigned to " + vendor.getClientName() + " at " + percentText(match.getCommission()) + ".", actor);
        MoneyActionLog.record("assign", actor, payment.getId().toString());
    }

    private AssignmentPlan plan(BillPayment payment, Bill bill, boolean willAssign, Commission match, String reason) {
        String biller = bill.getBillerName() == null || bill.getBillerName().isBlank()
                ? bill.getOperatorName() : bill.getBillerName();
        return new AssignmentPlan(
                payment.getId(),
                biller,
                bill.getCustomerBillAccountNumber(),
                payment.getBillAmount(),
                payment.getCorpName(),
                willAssign,
                match == null ? null : match.getVendorId(),
                match == null ? null : match.getVendorName(),
                match == null ? null : match.getCommission(),
                reason);
    }

    private Set<String> excludedVendors(BillPayment payment) {
        return rejectionRepository.findByBillPaymentId(payment.getId()).stream()
                .map(BillPaymentRejection::getVendorClientId)
                .collect(Collectors.toSet());
    }

    private String operatorId(Bill bill) {
        if (bill.getOperatorId() != null) {
            return bill.getOperatorId();
        }
        return operatorRepository.findActiveByCategory(bill.getCategoryId()).stream()
                .filter(operator -> bill.getOperatorName().equals(operator.operatorName()))
                .map(operator -> operator.operatorId())
                .findFirst()
                .orElse(null);
    }

    private boolean vendorIsActive(Commission rate) {
        return clientService.find(rate.getVendorId())
                .filter(client -> client.getClientType() == Client.ClientType.VENDOR)
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .isPresent();
    }

    private static String percentText(BigDecimal percent) {
        return percent.stripTrailingZeros().toPlainString() + "%";
    }

    public record AssignmentPlan(
            UUID billPaymentId,
            String billerName,
            String consumerNumber,
            BigDecimal amount,
            String corporateName,
            boolean willAssign,
            String vendorId,
            String vendorName,
            BigDecimal commissionPercent,
            String reason) {
    }
}
