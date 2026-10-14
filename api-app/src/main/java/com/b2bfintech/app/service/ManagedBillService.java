package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.billing.provider.kbill.KBillBbpsProvider;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.LocationEnablementService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final LocationEnablementService locationEnablementService;
    private final BillFetchResolutionService fetchResolutionService;
    private final AsyncBillFetch asyncBillFetch;
    private final BillActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    public ManagedBillService(
            BillRepository billRepository, CategoryEnablementService categoryEnablementService,
            LocationEnablementService locationEnablementService,
            BillFetchResolutionService fetchResolutionService, AsyncBillFetch asyncBillFetch,
            BillActivityLogService activityLogService, ObjectMapper objectMapper) {
        this.billRepository = billRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.locationEnablementService = locationEnablementService;
        this.fetchResolutionService = fetchResolutionService;
        this.asyncBillFetch = asyncBillFetch;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Bill createBill(UUID clientId, String categoryId, String locationId, String billerId, String billerName,
                            String customerBillAccountNumber, Map<String, String> parameters,
                            String customerMobileNumber, String createdBy) {
        categoryEnablementService.assertEnabled(clientId, categoryId);
        locationEnablementService.assertEnabled(locationId);

        Bill bill = billRepository
                .findByClientIdAndBillServiceCodeAndCustomerBillAccountNumber(clientId, categoryId, customerBillAccountNumber)
                .orElseGet(Bill::new);
        boolean isNew = bill.getId() == null;

        bill.setClientId(clientId);
        bill.setBillServiceCode(categoryId);
        bill.setProviderCode(KBillBbpsProvider.PROVIDER_CODE);
        bill.setProviderCategoryId(categoryId);
        bill.setProviderLocationId(locationId);
        bill.setProviderBillerId(billerId);
        bill.setBillerId(billerId);
        bill.setBillerName(billerName);
        bill.setCustomerBillAccountNumber(customerBillAccountNumber);
        bill.setFetchParameters(writeParameters(parameters));
        bill.setCustomerMobileNumber(customerMobileNumber);
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

    private void scheduleFetch(UUID clientId, UUID billId) {
        String tenant = TenantContext.getCurrentTenant();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncBillFetch.fetch(clientId, billId, tenant);
                }
            });
        } else {
            asyncBillFetch.fetch(clientId, billId, tenant);
        }
    }

    /** Manual "Retry fetch" from the Managed Bills screen - same fetch/resolve path as creation. */
    @Transactional
    public Bill retryFetch(UUID clientId, UUID billId) {
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
