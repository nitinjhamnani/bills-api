package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.spi.NotificationEvent;
import com.b2bfintech.common.spi.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Flags bill payments stuck with Paytrix (paid by the client, awaiting or mid pick-up)
 * past a configurable threshold. It logs a warning and sends a notification; it does not
 * change the payment.
 */
@Component
public class SlaEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationJob.class);
    private static final List<BillPayment.OrgStatus> IN_FLIGHT_STATUSES = List.of(
            BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS);

    private final BillPaymentRepository billPaymentRepository;
    private final NotificationProvider notificationProvider;
    private final long thresholdMinutes;

    public SlaEscalationJob(
            BillPaymentRepository billPaymentRepository, NotificationProvider notificationProvider,
            @Value("${app.billing.sla.threshold-minutes:1440}") long thresholdMinutes) {
        this.billPaymentRepository = billPaymentRepository;
        this.notificationProvider = notificationProvider;
        this.thresholdMinutes = thresholdMinutes;
    }

    @Scheduled(fixedDelayString = "${app.billing.sla.check-interval-ms:600000}", initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void checkForStuckBills() {
        Instant threshold = Instant.now().minus(thresholdMinutes, ChronoUnit.MINUTES);
        try {
            for (BillPayment payment : billPaymentRepository.findByOrgStatusInOrderByCreatedAtDesc(IN_FLIGHT_STATUSES)) {
                if (payment.getUpdatedAt().isAfter(threshold)) continue;
                log.warn("SLA breach: bill payment {} has been {} since {}",
                        payment.getId(), payment.getOrgStatus(), payment.getUpdatedAt());
                notificationProvider.send(new NotificationEvent(
                        "SLA_BREACH", null, payment.getClientPaidBy(), "paytrix",
                        Map.of("billPaymentId", payment.getId().toString(), "orgStatus", payment.getOrgStatus().name())));
            }
        } catch (RuntimeException ex) {
            log.error("SLA check failed", ex);
        }
    }
}
