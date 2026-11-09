package com.b2bfintech.app.service;

import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recomputes every wallet's balance from its ledger and compares it to the cached balance.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationJob(WalletAccountRepository walletAccountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Scheduled(fixedDelayString = "${app.billing.reconciliation.interval-ms:86400000}",
            initialDelayString = "${app.billing.worker.initial-delay-ms:15000}")
    public void reconcile() {
        int mismatches = 0;
        try {
            for (WalletAccount account : walletAccountRepository.findAll()) {
                var ledgerBalance = ledgerEntryRepository.computeBalanceFromLedger(account.getId());
                if (ledgerBalance.compareTo(account.getBalance()) != 0) {
                    mismatches++;
                    log.error("RECONCILIATION MISMATCH: client={} walletType={} cachedBalance={} ledgerBalance={}",
                            account.getClientId(), account.getWalletType(), account.getBalance(), ledgerBalance);
                }
            }
        } catch (RuntimeException ex) {
            log.error("Reconciliation failed", ex);
        }
        log.info("Reconciliation complete: {} mismatch(es) found", mismatches);
    }
}
