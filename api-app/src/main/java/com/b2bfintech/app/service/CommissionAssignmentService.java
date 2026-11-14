package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.entity.Commission;
import com.b2bfintech.organisation.service.CommissionService;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * After a corporate pays, the vendor with the highest approved, active commission for the bill's
 * operator and amount takes the bill. Bills that match nothing stay with Paytrix.
 */
@Service
public class CommissionAssignmentService {

    private final CommissionService commissionService;
    private final ClientService clientService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillActivityLogService activityLogService;
    private final CategoryOperatorRepository operatorRepository;

    public CommissionAssignmentService(
            CommissionService commissionService, ClientService clientService,
            BillPaymentRepository billPaymentRepository, BillActivityLogService activityLogService,
            CategoryOperatorRepository operatorRepository) {
        this.commissionService = commissionService;
        this.clientService = clientService;
        this.billPaymentRepository = billPaymentRepository;
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
        Set<String> excluded = excludedVendorIds == null ? Set.of() : excludedVendorIds;
        Optional<Commission> winner = commissionService
                .liveRates(bill.getCategoryId(), operatorId(bill))
                .stream()
                .filter(option -> !excluded.contains(option.getVendorId()))
                .filter(option -> option.covers(payment.getBillAmount()))
                .filter(this::vendorIsActive)
                .max(Comparator.comparing(Commission::getCommission)
                        .thenComparing(Commission::getCreatedAt, Comparator.reverseOrder()));
        if (winner.isEmpty()) {
            return;
        }
        Client vendor = clientService.getById(winner.get().getVendorId());
        payment.assignToVendor(vendor.getId(), vendor.getClientName(), Instant.now());
        billPaymentRepository.save(payment);
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ASSIGNED,
                "Assigned to " + vendor.getClientName() + ".", actor);
        MoneyActionLog.record("assign", actor, payment.getId().toString());
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
}
