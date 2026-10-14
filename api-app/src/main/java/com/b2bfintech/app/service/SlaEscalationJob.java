package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.spi.NotificationEvent;
import com.b2bfintech.common.spi.NotificationProvider;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
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
 * Flags bill payments stuck with the organisation (paid by the client, awaiting or mid pick-up)
 * past a configurable threshold - implementation plan &sect;8.5. Doesn't change anything itself;
 * it just surfaces stuck items (a WARN log + a dummy notification event) for a human to look at.
 */
@Component
public class SlaEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationJob.class);
    private static final List<BillPayment.OrgStatus> IN_FLIGHT_STATUSES = List.of(
            BillPayment.OrgStatus.PENDING, BillPayment.OrgStatus.IN_PROCESS);

    private final OrganisationRepository organisationRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final NotificationProvider notificationProvider;
    private final long thresholdMinutes;

    public SlaEscalationJob(
            OrganisationRepository organisationRepository, BillPaymentRepository billPaymentRepository, NotificationProvider notificationProvider,
            @Value("${app.billing.sla.threshold-minutes:1440}") long thresholdMinutes) {
        this.organisationRepository = organisationRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.notificationProvider = notificationProvider;
        this.thresholdMinutes = thresholdMinutes;
    }

    @Scheduled(fixedDelayString = "${app.billing.sla.check-interval-ms:600000}", initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void checkForStuckBills() {
        Instant threshold = Instant.now().minus(thresholdMinutes, ChronoUnit.MINUTES);
        for (Organisation organisation : organisationRepository.findAll()) {
            TenantContext.setCurrentTenant(organisation.tenantSchema());
            try {
                for (BillPayment payment : billPaymentRepository.findByOrgStatusInOrderByCreatedAtDesc(IN_FLIGHT_STATUSES)) {
                    if (payment.getUpdatedAt().isAfter(threshold)) continue;
                    log.warn("SLA breach: bill payment {} has been {} since {} (tenant {})",
                            payment.getId(), payment.getOrgStatus(), payment.getUpdatedAt(), organisation.tenantSchema());
                    notificationProvider.send(new NotificationEvent(
                            "SLA_BREACH", null, payment.getClientPaidBy(), organisation.tenantSchema(),
                            Map.of("billPaymentId", payment.getId().toString(), "orgStatus", payment.getOrgStatus().name())));
                }
            } catch (RuntimeException ex) {
                log.error("SLA check failed for tenant '{}'", organisation.tenantSchema(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
