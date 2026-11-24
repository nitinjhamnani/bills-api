package com.b2bfintech.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled autofetch is off. The per-bill switch still saves {@code auto_fetch_enabled}.
 * Creating a bill and Retry fetch still call the provider. Recurring calls stay off
 * until the per-bill cycle logic is in place, including when {@code BILLING_AUTO_FETCH_ENABLED}
 * is true.
 */
@Component
public class BillAutoFetchJob {

    @Scheduled(fixedDelayString = "${app.billing.auto-fetch.interval-ms:1800000}",
            initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void fetchDueBills() {
    }
}
