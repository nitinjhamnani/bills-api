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

import java.util.Map;
import java.util.UUID;

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

    public ManagedBillService(
            BillRepository billRepository, CategoryEnablementService categoryEnablementService,
            BillFetchResolutionService fetchResolutionService, AsyncBillFetch asyncBillFetch,
            BillActivityLogService activityLogService, ObjectMapper objectMapper,
            ClientAccountGuard clientAccountGuard, ObjectProvider<BbpsProvider> billProviders) {
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.fetchResolutionService = fetchResolutionService;
        this.asyncBillFetch = asyncBillFetch;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.clientAccountGuard = clientAccountGuard;
        this.billProviders = billProviders;
    }

    @Transactional
    public Bill createBill(UUID clientId, UUID branchId, String categoryId, String billerId, String billerName,
                            String customerBillAccountNumber, Map<String, String> parameters, String createdBy) {
        clientAccountGuard.requireActiveCorporate(clientId);
        categoryEnablementService.assertEnabled(clientId, categoryId);

        Bill bill = billRepository
                .findByClientIdAndBillServiceCodeAndCustomerBillAccountNumber(clientId, categoryId, customerBillAccountNumber)
                .orElseGet(Bill::new);
        boolean isNew = bill.getId() == null;
        if (!isNew && bill.getBranchId() != null && !branchId.equals(bill.getBranchId())) {
            throw new com.b2bfintech.common.exception.BadRequestException("This bill already belongs to another branch");
        }

        bill.setClientId(clientId);
        bill.setBranchId(branchId);
        bill.setBillServiceCode(categoryId);
        bill.setProviderCode(providerCode());
        bill.setProviderCategoryId(categoryId);
        bill.setProviderBillerId(billerId);
        bill.setBillerId(billerId);
        bill.setBillerName(billerName);
        bill.setCustomerBillAccountNumber(customerBillAccountNumber);
        bill.setFetchParameters(writeParameters(parameters));
        bill.setCustomerMobileNumber(createdBy);
        if (isNew) {
            bill.setPaymentStatus(Bill.PaymentStatus.PROCESSING);
            bill.setBillStatus(Bill.BillStatus.PROCESSING);
            bill.setCreatedBy(createdBy);
        }
        bill = billRepository.save(bill);
        if (isNew) {
            activityLogService.log(bill.getId(), null, BillPaymentLog.EventType.CREATED, "Bill created by " + createdBy, createdBy);
        }
        scheduleFetch(clientId, bill.getId());
        return bill;
    }

    private String providerCode() {
        BbpsProvider provider = billProviders.getIfAvailable();
        return provider == null ? "NONE" : provider.providerCode();
    }

    private void scheduleFetch(UUID clientId, UUID billId) {
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
    public Bill retryFetch(UUID clientId, UUID billId) {
        clientAccountGuard.requireActiveCorporate(clientId);
        Bill bill = getOwnedBill(clientId, billId);
        return fetchResolutionService.fetchAndResolve(bill);
    }

    @Transactional
    public Bill configureAutomation(UUID clientId, UUID billId, boolean autoFetchEnabled, boolean autoPayEnabled) {
        Bill bill = getOwnedBill(clientId, billId);
        bill.setAutoFetchEnabled(autoFetchEnabled);
        bill.setAutoPayEnabled(autoPayEnabled);
        return billRepository.save(bill);
    }

    public Bill getOwnedBill(UUID clientId, UUID billId) {
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
