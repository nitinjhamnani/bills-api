package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled recurring fetch for every Managed Bill with auto-fetch enabled - replaces the old
 * {@code AutoFetchJob}/{@code AutoPayJob} pair, since auto-pay is now just a branch inside
 * {@link BillFetchResolutionService#fetchAndResolve} rather than a second job scanning a second
 * flag. A bill already terminally FAILED (permanent fetch failure) is skipped - it only gets
 * retried by a manual "Retry fetch" action, not automatically.
 */
@Component
public class BillAutoFetchJob {

    private static final Logger log = LoggerFactory.getLogger(BillAutoFetchJob.class);

    private final OrganisationRepository organisationRepository;
    private final BillRepository billRepository;
    private final BillFetchResolutionService fetchResolutionService;

    public BillAutoFetchJob(
            OrganisationRepository organisationRepository, BillRepository billRepository,
            BillFetchResolutionService fetchResolutionService) {
        this.organisationRepository = organisationRepository;
        this.billRepository = billRepository;
        this.fetchResolutionService = fetchResolutionService;
    }

    @Scheduled(fixedDelayString = "${app.billing.auto-fetch.interval-ms:1800000}",
            initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void fetchDueBills() {
        for (Organisation organisation : organisationRepository.findAll()) {
            TenantContext.setCurrentTenant(organisation.tenantSchema());
            try {
                for (Bill bill : billRepository.findByAutoFetchEnabledTrue()) {
                    if (bill.getBillStatus() == Bill.BillStatus.FAILED) continue;
                    try {
                        fetchResolutionService.fetchAndResolve(bill);
                    } catch (RuntimeException ex) {
                        log.error("Auto-fetch failed for bill {}", bill.getId(), ex);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}
