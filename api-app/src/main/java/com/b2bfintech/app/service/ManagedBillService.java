package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
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
import java.util.Map;

/**
 * Managed Bills: creating one inserts the {@code bill} row immediately (PROCESSING) and returns it.
 * The provider fetch runs after that transaction commits, on a background thread, and
 * {@link BillFetchResolutionService} writes the real status. Re-creating a bill that already
 * exists for this client, category, and account starts another fetch on that same row.
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
    private static final SecureRandom IDS = new SecureRandom();
    private static final char[] ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    public ManagedBillService(
            BillRepository billRepository, CategoryEnablementService categoryEnablementService,
            BillFetchResolutionService fetchResolutionService, AsyncBillFetch asyncBillFetch,
            BillActivityLogService activityLogService, ObjectMapper objectMapper,
            ClientAccountGuard clientAccountGuard, ObjectProvider<BbpsProvider> billProviders,
            BbpsCategoryRepository categoryRepository) {
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.fetchResolutionService = fetchResolutionService;
        this.asyncBillFetch = asyncBillFetch;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.clientAccountGuard = clientAccountGuard;
        this.billProviders = billProviders;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Bill createBill(String clientId, String branchId, String categoryId, String billerId, String billerName,
                            String primaryParamName, String primaryParamValue, Map<String, String> parameters,
                            String customerMobile, String userId) {
        clientAccountGuard.requireActiveCorporate(clientId);
        categoryEnablementService.assertEnabled(clientId, categoryId);
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new com.b2bfintech.common.exception.BadRequestException("Unknown category: " + categoryId));

        Bill bill = billRepository
                .findFirstBySourceIdAndCategoryNameAndOperatorNameAndPrimaryParamValue(
                        clientId, category.name(), billerName, primaryParamValue)
                .orElseGet(Bill::new);
        boolean isNew = bill.getId() == null;
        if (!isNew && bill.getBranchId() != null && !branchId.equals(bill.getBranchId())) {
            throw new com.b2bfintech.common.exception.BadRequestException("This bill already belongs to another branch");
        }

        if (isNew) {
            bill.setBillId(newBillId());
            bill.setSourceType(Bill.SourceType.CORP);
            bill.setUserId(userId);
        }
        bill.setSourceId(clientId);
        bill.setBranchId(branchId);
        bill.setCategoryId(category.id());
        bill.setCategoryName(category.name());
        bill.setOperatorName(billerName);
        bill.setOperatorId(billerId);
        bill.setPrimaryParamName(primaryParamName);
        bill.setPrimaryParamValue(primaryParamValue);
        bill.setCustomerMobile(customerMobile);
        bill.setInputParamJson(writeParameters(parameters));
        bill = billRepository.save(bill);
        if (isNew) {
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.CREATED, "Bill created", userId);
            MoneyActionLog.record("create", userId, bill.getId());
        }
        scheduleFetch(clientId, bill.getId());
        return bill;
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
