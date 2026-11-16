package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Managed Bills: creating one inserts the {@code bill} row immediately (PROCESSING) and returns it.
 * The provider fetch runs after that transaction commits, on a background thread, and
 * {@link BillFetchResolutionService} writes the real status. A corporate can manage each account
 * number once. The same primary parameter value cannot be added again on any branch.
 */
@Service
public class ManagedBillService {

    private final BillRepository billRepository;
    private final CategoryEnablementService categoryEnablementService;
    private final BillFetchResolutionService fetchResolutionService;
    private final AsyncBillFetch asyncBillFetch;
    private final BillActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final ClientAccountGuard clientAccountGuard;
    private final ObjectProvider<BbpsProvider> billProviders;
    private final BbpsCategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private static final SecureRandom IDS = new SecureRandom();
    private static final char[] ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    public ManagedBillService(
            BillRepository billRepository, CategoryEnablementService categoryEnablementService,
            BillFetchResolutionService fetchResolutionService, AsyncBillFetch asyncBillFetch,
            BillActivityLogService activityLogService, ObjectMapper objectMapper,
            ClientAccountGuard clientAccountGuard, ObjectProvider<BbpsProvider> billProviders,
            BbpsCategoryRepository categoryRepository, BranchRepository branchRepository) {
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.fetchResolutionService = fetchResolutionService;
        this.asyncBillFetch = asyncBillFetch;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.clientAccountGuard = clientAccountGuard;
        this.billProviders = billProviders;
        this.categoryRepository = categoryRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional
    public Bill createBill(String clientId, String branchId, String categoryId, String billerId, String billerName,
                            String primaryParamName, String primaryParamValue, Map<String, String> parameters,
                            String customerMobile, String userId) {
        clientAccountGuard.requireActiveCorporate(clientId);
        categoryEnablementService.assertEnabled(clientId, categoryId);
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new com.b2bfintech.common.exception.BadRequestException("Unknown category: " + categoryId));

        String accountNumber = primaryParamValue == null ? "" : primaryParamValue.trim();
        if (accountNumber.isEmpty()) {
            throw new BadRequestException("Enter the account number");
        }
        rejectDuplicateAccount(clientId, accountNumber);

        Bill bill = new Bill();
        bill.setBillId(newBillId());
        bill.setSourceType(Bill.SourceType.CORP);
        bill.setUserId(userId);
        bill.setSourceId(clientId);
        bill.setBranchId(branchId);
        bill.setCategoryId(category.id());
        bill.setCategoryName(category.name());
        bill.setOperatorName(billerName);
        bill.setOperatorId(billerId);
        bill.setPrimaryParamName(primaryParamName);
        bill.setPrimaryParamValue(accountNumber);
        bill.setCustomerMobile(customerMobile);
        Map<String, String> stored = parameters == null ? new java.util.HashMap<>() : new java.util.HashMap<>(parameters);
        stored.put(primaryParamName, accountNumber);
        bill.setInputParamJson(writeParameters(stored));
        try {
            bill = billRepository.saveAndFlush(bill);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new BadRequestException("This account number is already managed for this company.");
        }
        activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.CREATED, "Bill created", userId);
        MoneyActionLog.record("create", userId, bill.getId());
        scheduleFetch(clientId, bill.getId());
        return bill;
    }

    private void rejectDuplicateAccount(String clientId, String accountNumber) {
        List<Bill> existing = billRepository.findByClientAndPrimaryParamValue(clientId, accountNumber);
        if (existing.isEmpty()) {
            return;
        }
        String branchName = branchRepository.findById(existing.get(0).getBranchId())
                .map(branch -> branch.getName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
        throw new BadRequestException(branchName == null
                ? "This account number is already managed for this company."
                : "This account number is already managed on the " + branchName + " branch.");
    }

    private String newBillId() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder id = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                id.append(ID_CHARS[IDS.nextInt(ID_CHARS.length)]);
            }
            if (!billRepository.existsById(id.toString())) {
                return id.toString();
            }
        }
        throw new com.b2bfintech.common.exception.BadRequestException("Could not assign a bill id");
    }

    private String providerCode() {
        BbpsProvider provider = billProviders.getIfAvailable();
        return provider == null ? "NONE" : provider.providerCode();
    }

    private void scheduleFetch(String clientId, String billId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncBillFetch.fetch(clientId, billId);
                }
            });
        } else {
            asyncBillFetch.fetch(clientId, billId);
        }
    }

    /** Manual "Retry fetch" from the Managed Bills screen - same fetch/resolve path as creation. */
    @Transactional
    public Bill retryFetch(String clientId, String billId) {
        clientAccountGuard.requireActiveCorporate(clientId);
        Bill bill = getOwnedBill(clientId, billId);
        return fetchResolutionService.fetchAndResolve(bill);
    }

    @Transactional
    public Bill configureAutomation(String clientId, String billId, boolean autoFetchEnabled, boolean autoPayEnabled) {
        return getOwnedBill(clientId, billId);
    }

    public Bill getOwnedBill(String clientId, String billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        if (!bill.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Bill not found");
        }
        return bill;
    }

    private String writeParameters(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters == null ? Map.of() : parameters);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store bill parameters", ex);
        }
    }
}
