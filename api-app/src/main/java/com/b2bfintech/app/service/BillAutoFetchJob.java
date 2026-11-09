package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.repository.BillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled recurring fetch for every Managed Bill with auto-fetch enabled. A bill already
 * terminally FAILED is skipped — it is retried only by a manual "Retry fetch" action.
 */
@Component
public class BillAutoFetchJob {

    private static final Logger log = LoggerFactory.getLogger(BillAutoFetchJob.class);

    private final BillRepository billRepository;
    private final BillFetchResolutionService fetchResolutionService;
    private final boolean enabled;

    public BillAutoFetchJob(
            BillRepository billRepository,
            BillFetchResolutionService fetchResolutionService,
            @Value("${app.billing.auto-fetch.enabled:true}") boolean enabled) {
        this.billRepository = billRepository;
        this.fetchResolutionService = fetchResolutionService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.billing.auto-fetch.interval-ms:1800000}",
            initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void fetchDueBills() {
        if (!enabled) {
            return;
        }
        for (Bill bill : billRepository.findByAutoFetchEnabledTrue()) {
            if (bill.getBillStatus() == Bill.BillStatus.FAILED) continue;
            try {
                fetchResolutionService.fetchAndResolve(bill);
            } catch (RuntimeException ex) {
                log.error("Auto-fetch failed for bill {}", bill.getId(), ex);
            }
        }
    }
}
