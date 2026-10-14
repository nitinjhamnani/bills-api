package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Runs a bill fetch off the request thread, on the tenant that created the bill. */
@Service
public class AsyncBillFetch {

    private static final Logger log = LoggerFactory.getLogger(AsyncBillFetch.class);

    private final BillRepository billRepository;
    private final BillFetchResolutionService fetchResolutionService;

    public AsyncBillFetch(BillRepository billRepository, BillFetchResolutionService fetchResolutionService) {
        this.billRepository = billRepository;
        this.fetchResolutionService = fetchResolutionService;
    }

    @Async
    public void fetch(UUID clientId, UUID billId, String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            log.error("Skipping async fetch for bill {} because no tenant was set", billId);
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantSchema);
            Bill bill = billRepository.findById(billId).orElse(null);
            if (bill == null || !clientId.equals(bill.getClientId())) {
                log.error("Skipping async fetch for bill {} because it is not in tenant {}", billId, tenantSchema);
                return;
            }
            fetchResolutionService.fetchAndResolve(bill);
        } catch (RuntimeException ex) {
            log.error("Async bill fetch failed for {}", billId, ex);
        } finally {
            TenantContext.clear();
        }
    }
}
