package com.b2bfintech.app.service;

import com.b2bfintech.app.controller.ReportDtos;
import com.b2bfintech.app.controller.ReportQueryService;
import com.b2bfintech.common.spi.NotificationEvent;
import com.b2bfintech.common.spi.NotificationProvider;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First of each month, IST: notify each client admin with the previous calendar month's
 * wallet-statement totals. Delivery is the {@link NotificationProvider} (dummy logs today).
 */
@Component
public class MonthlyClientStatementJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyClientStatementJob.class);

    private final OrganisationRepository organisationRepository;
    private final ClientRepository clientRepository;
    private final TenantUserService tenantUserService;
    private final ReportQueryService reportQueryService;
    private final NotificationProvider notificationProvider;

    public MonthlyClientStatementJob(
            OrganisationRepository organisationRepository,
            ClientRepository clientRepository,
            TenantUserService tenantUserService,
            ReportQueryService reportQueryService,
            NotificationProvider notificationProvider) {
        this.organisationRepository = organisationRepository;
        this.clientRepository = clientRepository;
        this.tenantUserService = tenantUserService;
        this.reportQueryService = reportQueryService;
        this.notificationProvider = notificationProvider;
    }

    @Scheduled(cron = "${app.billing.statement.cron:0 30 8 1 * *}", zone = "Asia/Kolkata")
    public void sendPreviousMonthStatements() {
        LocalDate firstOfThisMonth = LocalDate.now(ReportDtos.IST).withDayOfMonth(1);
        LocalDate from = firstOfThisMonth.minusMonths(1);
        LocalDate to = firstOfThisMonth.minusDays(1);
        var rangeStart = from.atStartOfDay(ReportDtos.IST).toInstant();
        var rangeEnd = to.plusDays(1).atStartOfDay(ReportDtos.IST).toInstant();

        for (Organisation organisation : organisationRepository.findAll()) {
            TenantContext.setCurrentTenant(organisation.tenantSchema());
            try {
                for (Client client : clientRepository.findAll()) {
                    ReportDtos.WalletStatementDto statement =
                            reportQueryService.walletStatement(client.getId(), rangeStart, rangeEnd);
                    for (AppUser admin : tenantUserService.listForClient(client.getId())) {
                        if (!"CLIENT_ADMIN".equals(admin.getRole().getName())) continue;
                        if (admin.getStatus() != AppUser.UserStatus.ACTIVE) continue;
                        Map<String, String> data = new LinkedHashMap<>();
                        data.put("clientId", client.getId().toString());
                        data.put("clientName", client.getClientName());
                        data.put("from", from.toString());
                        data.put("to", to.toString());
                        data.put("openingBalance", statement.openingBalance().toPlainString());
                        data.put("closingBalance", statement.closingBalance().toPlainString());
                        data.put("totalCredits", statement.totalCredits().toPlainString());
                        data.put("totalDebits", statement.totalDebits().toPlainString());
                        notificationProvider.send(new NotificationEvent(
                                "MONTHLY_WALLET_STATEMENT",
                                admin.getPhoneNumber(),
                                admin.getId().toString(),
                                organisation.tenantSchema(),
                                data));
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Monthly statement notify failed for tenant '{}'", organisation.tenantSchema(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
