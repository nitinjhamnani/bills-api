package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.provider.branchx.BranchXMapper;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Saves a fetch only when the provider returns HTTP 200. Any other status leaves the bill as it
 * was, with no fetch row.
 */
@Service
public class BillFetchResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BillFetchResolutionService.class);

    private final BillRepository billRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillingProvider billingProvider;
    private final BillActivityLogService activityLogService;
    private final CategoryOperatorRepository operatorRepository;
    private final ObjectMapper objectMapper;

    public BillFetchResolutionService(
            BillRepository billRepository, BillFetchRepository billFetchRepository,
            BillingProvider billingProvider, BillActivityLogService activityLogService,
            CategoryOperatorRepository operatorRepository, ObjectMapper objectMapper) {
        this.billRepository = billRepository;
        this.billFetchRepository = billFetchRepository;
        this.billingProvider = billingProvider;
        this.activityLogService = activityLogService;
        this.operatorRepository = operatorRepository;
        this.objectMapper = objectMapper;
    }

    /** Reads the current due bill and does not save it. Settlement uses this so a check cannot open a new cycle. */
    public BillingProvider.FetchedBill fetchCurrent(Bill bill) {
        return billingProvider.fetchBill(commandOrThrow(bill));
    }

    /** Calls the provider for the checker and returns what it said. Nothing is saved. */
    public ProviderCheck checkCurrent(Bill bill) {
        BbpsProvider.BillFetchCommand command;
        try {
            command = commandOrThrow(bill);
        } catch (BillFetchFailedException ex) {
            return new ProviderCheck(null, null, null, ex.getMessage());
        }
        BillingProvider.HttpBillFetch http = billingProvider.fetchBillHttp(command);
        if (http.statusCode() != 200) {
            return new ProviderCheck(http.statusCode(), http.body(), null, "The provider returned HTTP " + http.statusCode());
        }
        try {
            BillingProvider.FetchedBill fetched = BranchXMapper.bill(objectMapper.readTree(http.body() == null ? "" : http.body()));
            return new ProviderCheck(http.statusCode(), http.body(), fetched.amount(), null);
        } catch (BillFetchFailedException | JsonProcessingException ex) {
            return new ProviderCheck(http.statusCode(), http.body(), null, ex.getMessage());
        }
    }

    /** {@code amount} is null when the response had no bill amount. {@code error} explains why. */
    public record ProviderCheck(Integer httpStatus, String body, java.math.BigDecimal amount, String error) {
    }

    @Transactional
    public Bill fetchAndResolve(Bill bill) {
        try {
            BbpsProvider.BillFetchCommand command = commandFor(bill);
            if (command == null) {
                return bill;
            }
            MDC.put("billId", bill.getId());
            BillingProvider.HttpBillFetch http = billingProvider.fetchBillHttp(command);
            if (http.statusCode() != 200) {
                log.info("Bill {} fetch returned HTTP {} and was not stored", bill.getId(), http.statusCode());
                return bill;
            }
            return storeFetch(bill, http.body());
        } finally {
            MDC.remove("billId");
        }
    }

    private BbpsProvider.BillFetchCommand commandFor(Bill bill) {
        try {
            return commandOrThrow(bill);
        } catch (BillFetchFailedException ex) {
            log.info("Bill {} was not fetched: {}", bill.getId(), ex.getMessage());
            return null;
        }
    }

    private BbpsProvider.BillFetchCommand commandOrThrow(Bill bill) {
        String operatorId = bill.getOperatorId();
        if (operatorId == null || operatorId.isBlank()) {
            operatorId = operatorRepository.findActiveByCategory(bill.getCategoryId()).stream()
                    .filter(operator -> bill.getOperatorName().equals(operator.operatorName()))
                    .map(operator -> operator.operatorId())
                    .findFirst()
                    .orElse(null);
            bill.setOperatorId(operatorId);
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new BillFetchFailedException("Bill is not linked to a provider biller", false);
        }
        String mobile = bill.getCustomerMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            throw new BillFetchFailedException("Enter the mobile number for this bill before it can be fetched", true);
        }
        String clientRefId = "BF-" + bill.getId() + "-" + Instant.now().toEpochMilli();
        return new BbpsProvider.BillFetchCommand(
                operatorId, bill.getCategoryName(), bill.getPrimaryParamValue(), readParameters(bill),
                mobile, bill.getCustomerName(), clientRefId);
    }

    private Bill storeFetch(Bill bill, String body) {
        BillFetch fetch = new BillFetch();
        fetch.setBillId(bill.getId());
        fetch.setFetchedOn(Instant.now());
        fetch.setFetchProvider(bill.getProviderCode() == null ? "BRANCHX" : bill.getProviderCode());
        fetch.setFetchSnapshotJson(jsonOrNull(body));
        try {
            BillingProvider.FetchedBill fetched = BranchXMapper.bill(objectMapper.readTree(body == null ? "" : body));
            boolean due = fetched.amount() != null && fetched.amount().signum() > 0;
            fetch.setBillAmount(fetched.amount());
            fetch.setBillCycle(blankToNull(fetched.billPeriod()));
            fetch.setBillDueDate(dateOrNull(fetched.dueDate()));
            fetch.setBillGeneratedDate(dateOrNull(fetched.billDate()));
            fetch.setBillConsumerName(blankToNull(fetched.customerName()));
            fetch.setBillStatus(due ? BillFetch.BillStatus.DUE : BillFetch.BillStatus.PAID);
            fetch.setBillPaymentStatus(due ? BillFetch.PaymentStatus.PENDING : BillFetch.PaymentStatus.PAID);
            fetch.setFetchStatus(BillFetch.FetchStatus.SUCCESS);
            fetch.setFetchRemarks("Fetched Successfully");
            fetch = billFetchRepository.save(fetch);
            bill.setLatestFetch(fetch);
            billRepository.save(bill);
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.FETCH_SUCCESS,
                    due ? "Fetched - due Rs." + fetched.amount() : "Fetched - already paid", null);
        } catch (BillFetchFailedException | JsonProcessingException ex) {
            String remarks = ex instanceof BillFetchFailedException failed ? failed.getMessage() : "The provider response could not be read";
            fetch.setBillStatus(BillFetch.BillStatus.FAILED);
            fetch.setBillPaymentStatus(BillFetch.PaymentStatus.PENDING);
            fetch.setFetchStatus(BillFetch.FetchStatus.FAILED);
            fetch.setFetchRemarks(abbreviate(remarks));
            fetch = billFetchRepository.save(fetch);
            bill.setLatestFetch(fetch);
            billRepository.save(bill);
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.FETCH_FAILED_PERMANENT, abbreviate(remarks), null);
        }
        return bill;
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

    private String jsonOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static LocalDate dateOrNull(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
