package com.b2bfintech.platform.service;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Loads categories into the platform table. Runs once the application is up, without
 * blocking startup, and again every two hours. A provider outage leaves the previous rows in place.
 */
@Service
public class BbpsCatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(BbpsCatalogSyncService.class);

    private final ObjectProvider<BbpsProvider> providers;
    private final BbpsCategoryRepository categoryRepository;
    private final TransactionTemplate transactions;

    public BbpsCatalogSyncService(
            ObjectProvider<BbpsProvider> providers,
            BbpsCategoryRepository categoryRepository,
            PlatformTransactionManager transactionManager) {
        this.providers = providers;
        this.categoryRepository = categoryRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            SyncSummary summary = sync();
            log.info("Startup BBPS catalog sync {}", summary);
        } catch (RuntimeException ex) {
            log.error("Startup BBPS catalog sync failed; the API will keep serving the last synced catalog", ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.billing.catalog-refresh-ms:7200000}",
            initialDelayString = "${app.billing.catalog-refresh-ms:7200000}")
    public void scheduledRefresh() {
        try {
            log.info("Scheduled BBPS catalog sync {}", sync());
        } catch (RuntimeException ex) {
            log.error("Scheduled BBPS catalog sync failed", ex);
        }
    }

    public SyncSummary sync() {
        BbpsProvider provider = providers.getIfAvailable();
        if (provider == null) {
            log.info("No BBPS provider configured; catalog sync skipped");
            return new SyncSummary(true, 0, null);
        }
        int categories = syncCategories(provider);
        return new SyncSummary(false, categories, null);
    }

    public int syncCategories(BbpsProvider provider) {
        List<BbpsProvider.ProviderCategory> fetched = provider.fetchCategories();
        if (fetched.isEmpty()) {
            log.warn("Category sync returned no rows; leaving the existing categories in place");
            return 0;
        }
        transactions.executeWithoutResult(status -> {
            categoryRepository.deactivateAll(provider.providerCode());
            for (BbpsProvider.ProviderCategory category : fetched) {
                categoryRepository.upsert(provider.providerCode(), category);
            }
        });
        return fetched.size();
    }

    public record SyncSummary(boolean skipped, int categories, String error) {
    }
}
