package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.CommissionRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * After a corporate pays, the vendor with the highest live commission on the matching rule
 * is recorded on the payment. The percent is copied when that vendor marks the bill paid.
 * Bills that match nothing stay with Paytrix.
 */
@Service
public class CommissionAssignmentService {

    private final CommissionRuleService commissionRuleService;
    private final ClientService clientService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillActivityLogService activityLogService;

    public CommissionAssignmentService(
            CommissionRuleService commissionRuleService, ClientService clientService,
            BillPaymentRepository billPaymentRepository, BillActivityLogService activityLogService) {
        this.commissionRuleService = commissionRuleService;
        this.clientService = clientService;
        this.billPaymentRepository = billPaymentRepository;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void assignIfMatched(BillPayment payment, Bill bill, String actor) {
        assignIfMatched(payment, bill, actor, Set.of());
    }

    /** {@code excludedVendorIds} are vendors who already sent this payment back. */
    @Transactional
    public void assignIfMatched(BillPayment payment, Bill bill, String actor, Set<UUID> excludedVendorIds) {
        if (payment.getAssignedVendorClientId() != null || payment.getAmount() == null) {
            return;
        }
        Set<UUID> excluded = excludedVendorIds == null ? Set.of() : excludedVendorIds;
        Optional<CommissionRuleService.AssignmentOption> winner = commissionRuleService
                .liveRates(bill.getProviderCategoryId(), bill.getProviderBillerId())
                .stream()
                .filter(option -> !excluded.contains(option.vendorClientId()))
                .filter(option -> option.range().contains(payment.getAmount()))
                .filter(this::vendorIsActive)
                .max(Comparator.comparing(CommissionRuleService.AssignmentOption::commissionPercent)
                        .thenComparing(CommissionRuleService.AssignmentOption::createdAt, Comparator.reverseOrder()));
        if (winner.isEmpty()) {
            return;
        }
        CommissionRuleService.AssignmentOption rate = winner.get();
        payment.setAssignedVendorClientId(rate.vendorClientId());
        payment.setCommissionRuleId(rate.ruleId());
        payment.setCommissionPercent(null);
        payment.setCommissionAmount(null);
        payment.setAssignedAt(Instant.now());
        payment.setVendorStage(BillPayment.VendorStage.ASSIGNED);
        billPaymentRepository.save(payment);
        Client vendor = clientService.getById(rate.vendorClientId());
        activityLogService.log(payment.getBillId(), payment.getId(), BillPaymentLog.EventType.ASSIGNED,
                "Assigned to " + vendor.getClientName() + ". The commission is taken when the vendor marks the bill paid.",
                actor);
    }

    /** Waiting bills show the vendor's current rate. A marked or confirmed bill keeps the rate that was saved. */
    @Transactional(readOnly = true)
    public CommissionQuote quote(BillPayment payment) {
        if (payment.getOrgStatus() != BillPayment.OrgStatus.PENDING) {
            return new CommissionQuote(payment.getCommissionPercent(), payment.getCommissionAmount());
        }
        Optional<BigDecimal> percent = commissionRuleService.livePercent(
                payment.getCommissionRuleId(), payment.getAssignedVendorClientId());
        if (percent.isEmpty() || payment.getAmount() == null) {
            return new CommissionQuote(null, null);
        }
        BigDecimal amount = payment.getAmount()
                .multiply(percent.get())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new CommissionQuote(percent.get(), amount);
    }

    /** The rate this vendor would pay now. Empty when the rule or the vendor is not live. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> livePercent(BillPayment payment) {
        return commissionRuleService.livePercent(payment.getCommissionRuleId(), payment.getAssignedVendorClientId());
    }

    public record CommissionQuote(BigDecimal percent, BigDecimal amount) {
    }

    private boolean vendorIsActive(CommissionRuleService.AssignmentOption rate) {
        return clientService.find(rate.vendorClientId())
                .filter(client -> client.getClientType() == Client.ClientType.VENDOR)
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .isPresent();
    }
}
