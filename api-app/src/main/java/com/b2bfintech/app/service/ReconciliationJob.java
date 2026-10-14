package com.b2bfintech.app.service;

import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily(-ish) reconciliation: recomputes every wallet's balance from its own ledger and compares
 * it to the cached {@code wallet_account.balance} - implementation plan &sect;6. With a dummy
 * payment partner there's no external statement to reconcile against yet; this at least
 * guarantees the cached balance and the audit trail never silently drift apart, which is the
 * same invariant a real statement-based reconciliation depends on.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final OrganisationRepository organisationRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationJob(OrganisationRepository organisationRepository, WalletAccountRepository walletAccountRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.organisationRepository = organisationRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Scheduled(fixedDelayString = "${app.billing.reconciliation.interval-ms:86400000}",
            initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void reconcile() {
        int mismatches = 0;
        for (Organisation organisation : organisationRepository.findAll()) {
            TenantContext.setCurrentTenant(organisation.tenantSchema());
            try {
                for (WalletAccount account : walletAccountRepository.findAll()) {
                    var ledgerBalance = ledgerEntryRepository.computeBalanceFromLedger(account.getId());
                    if (ledgerBalance.compareTo(account.getBalance()) != 0) {
                        mismatches++;
                        log.error("RECONCILIATION MISMATCH: tenant={} client={} walletType={} cachedBalance={} ledgerBalance={}",
                                organisation.tenantSchema(), account.getClientId(), account.getWalletType(),
                                account.getBalance(), ledgerBalance);
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Reconciliation failed for tenant '{}'", organisation.tenantSchema(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Reconciliation complete: {} mismatch(es) found", mismatches);
    }
}
