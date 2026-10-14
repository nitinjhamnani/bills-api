package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place the bill status state machine is implemented - shared by bill creation
 * ({@link ManagedBillService}) and the scheduled recurring fetch ({@link BillAutoFetchJob}), so a
 * manual create and an automatic cycle behave identically:
 *
 * <ul>
 *   <li>fetch success, due &gt; 0 -&gt; payment_status PENDING / bill_status DUE, and a new
 *       {@link BillPayment} cycle row is opened (or refreshed, if this cycle's due date already
 *       has one still sitting at org_status NA)</li>
 *   <li>fetch success, due = 0 -&gt; PAID / PAID, no payment cycle</li>
 *   <li>fetch failure, transient -&gt; PROCESSING / PROCESSING (retried next scheduled tick)</li>
 *   <li>fetch failure, permanent -&gt; FAILED / FAILED (terminal - no auto-retry)</li>
 * </ul>
 */
@Service
public class BillFetchResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BillFetchResolutionService.class);

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillingProvider billingProvider;
    private final BillActivityLogService activityLogService;
    private final ClientPaymentService clientPaymentService;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    public BillFetchResolutionService(
            BillRepository billRepository, BillPaymentRepository billPaymentRepository, BillingProvider billingProvider,
            BillActivityLogService activityLogService, ClientPaymentService clientPaymentService,
            ClientRepository clientRepository, ObjectMapper objectMapper) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.billingProvider = billingProvider;
        this.activityLogService = activityLogService;
        this.clientPaymentService = clientPaymentService;
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Bill fetchAndResolve(Bill bill) {
        try {
            BbpsProvider.BillFetchCommand command = commandFor(bill);
            if (command == null) {
                return bill;
            }
            MDC.put("billId", bill.getId().toString());
            BillingProvider.FetchedBill fetched = billingProvider.fetchBill(command);
            return resolveSuccess(bill, fetched, command.clientReferenceId());
        } catch (BillFetchFailedException ex) {
            return resolveFailure(bill, ex);
        } finally {
            MDC.remove("billId");
        }
    }

    private BbpsProvider.BillFetchCommand commandFor(Bill bill) {
        if (bill.getProviderCode() != null && (bill.getProviderBillerId() == null || bill.getProviderBillerId().isBlank())) {
            resolveFailure(bill, new BillFetchFailedException("Bill is not linked to a provider biller", false));
            return null;
        }
        String mobile = bill.getCustomerMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            resolveFailure(bill, new BillFetchFailedException("Customer mobile number is required before a bill can be fetched", true));
            return null;
        }
        String sender = bill.getCustomerName();
        if (sender == null || sender.isBlank()) {
            sender = clientRepository.findById(bill.getClientId()).map(Client::getClientName).orElse(null);
        }
        if (sender == null || sender.isBlank()) {
            resolveFailure(bill, new BillFetchFailedException("Sender name is required before a bill can be fetched", true));
            return null;
        }
        String billerId = bill.getProviderBillerId() != null ? bill.getProviderBillerId() : bill.getBillerId();
        String categoryId = bill.getProviderCategoryId() != null ? bill.getProviderCategoryId() : bill.getBillServiceCode();
        String clientRefId = "BF-" + bill.getId().toString().substring(0, 8) + "-" + Instant.now().toEpochMilli();
        return new BbpsProvider.BillFetchCommand(
                billerId, categoryId, bill.getCustomerBillAccountNumber(), readParameters(bill),
                mobile, sender, clientRefId);
    }

    private Bill resolveSuccess(Bill bill, BillingProvider.FetchedBill fetched, String clientRefId) {
        bill.setCustomerName(fetched.customerName());
        bill.setAmount(fetched.amount());
        bill.setAmountBreakdown(serialize(fetched.breakdown()));
        bill.setBillNumber(fetched.billNumber());
        bill.setBillDate(fetched.billDate());
        bill.setDueDate(fetched.dueDate());
        bill.setBillPeriod(fetched.billPeriod());
        bill.setLastFetchResponseSnapshot(serializeSnapshot(fetched));
        bill.setLastFetchAt(Instant.now());
        bill.setLastFetchFailed(false);
        bill.setLastFetchFailureReason(null);

        boolean alreadyPaidThisCycle = fetched.dueDate() != null && fetched.dueDate().equals(bill.getLastPaidDueDate());
        boolean due = !alreadyPaidThisCycle && fetched.amount() != null && fetched.amount().signum() > 0;

        if (due) {
            bill.setPaymentStatus(Bill.PaymentStatus.PENDING);
            bill.setBillStatus(Bill.BillStatus.DUE);
            billRepository.save(bill);
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.FETCH_SUCCESS,
                    "Fetched - due Rs." + fetched.amount() + " (ref " + clientRefId + ", code 01)", null);
            BillPayment payment = openOrRefreshPaymentCycle(bill, fetched);
            maybeAutoPay(bill, payment);
        } else {
            bill.setPaymentStatus(Bill.PaymentStatus.PAID);
            bill.setBillStatus(Bill.BillStatus.PAID);
            billRepository.save(bill);
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.FETCH_SUCCESS,
                    "Fetched - already paid (ref " + clientRefId + ", code 01)", null);
        }
        return bill;
    }

    private Bill resolveFailure(Bill bill, BillFetchFailedException ex) {
        bill.setLastFetchAt(Instant.now());
        bill.setLastFetchFailed(true);
        bill.setLastFetchFailureReason(ex.getMessage());
        if (ex.isPermanent()) {
            bill.setPaymentStatus(Bill.PaymentStatus.FAILED);
            bill.setBillStatus(Bill.BillStatus.FAILED);
        } else {
            bill.setPaymentStatus(Bill.PaymentStatus.PROCESSING);
            bill.setBillStatus(Bill.BillStatus.PROCESSING);
        }
        billRepository.save(bill);
        activityLogService.log(bill.getId(), null,
                ex.isPermanent() ? BillPaymentLog.EventType.FETCH_FAILED_PERMANENT : BillPaymentLog.EventType.FETCH_FAILED_TRANSIENT,
                ex.getMessage(), null);
        return bill;
    }

    /** Same due date already has an open (org_status NA) payment cycle - refresh it in place
     * instead of opening a duplicate, since a scheduled re-fetch can report the same due bill
     * more than once before the client acts on it. */
    private BillPayment openOrRefreshPaymentCycle(Bill bill, BillingProvider.FetchedBill fetched) {
        String cycleKey = fetched.dueDate() != null ? fetched.dueDate()
                : Objects.requireNonNullElse(fetched.billNumber(), bill.getId().toString());
        String idempotencyKey = "billpay-" + bill.getId() + "-" + cycleKey;

        BillPayment payment = billPaymentRepository.findByIdempotencyKey(idempotencyKey).orElseGet(BillPayment::new);
        boolean isNew = payment.getId() == null;

        payment.setBillId(bill.getId());
        payment.setClientId(bill.getClientId());
        payment.setAmount(fetched.amount());
        payment.setAmountBreakdown(bill.getAmountBreakdown());
        payment.setBillNumber(fetched.billNumber());
        payment.setBillDate(fetched.billDate());
        payment.setDueDate(fetched.dueDate());
        payment.setBillPeriod(fetched.billPeriod());
        payment.setApiResponseSnapshot(bill.getLastFetchResponseSnapshot());
        payment.setIdempotencyKey(idempotencyKey);
        if (isNew) {
            payment.setOrgStatus(BillPayment.OrgStatus.NA);
            payment.setPaymentStatus(Bill.PaymentStatus.PENDING);
            payment.setBillStatus(Bill.BillStatus.DUE);
        }
        payment = billPaymentRepository.save(payment);

        if (isNew) {
            activityLogService.log(bill.getId(), payment.getId(), BillPaymentLog.EventType.FETCH_SUCCESS,
                    "Payment cycle opened, due Rs." + fetched.amount(), null);
        }
        return payment;
    }

    private void maybeAutoPay(Bill bill, BillPayment payment) {
        if (!bill.isAutoPayEnabled() || payment.getOrgStatus() != BillPayment.OrgStatus.NA) return;
        try {
            clientPaymentService.payOne(bill.getClientId(), payment.getId(), "AUTO_PAY");
        } catch (RuntimeException ex) {
            log.error("Auto-pay failed for bill {} payment {}", bill.getId(), payment.getId(), ex);
        }
    }

    private String serialize(List<BillingProvider.AmountComponent> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception ex) {
            log.warn("Could not serialize amount breakdown, dropping it", ex);
            return null;
        }
    }

    private Map<String, String> readParameters(Bill bill) {
        if (bill.getFetchParameters() == null || bill.getFetchParameters().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(bill.getFetchParameters(), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Could not read stored bill parameters for {}", bill.getId(), ex);
            return Map.of();
        }
    }

    private String serializeSnapshot(BillingProvider.FetchedBill fetched) {
        try {
            return objectMapper.writeValueAsString(fetched);
        } catch (Exception ex) {
            log.warn("Could not serialize fetch response snapshot", ex);
            return null;
        }
    }
}
