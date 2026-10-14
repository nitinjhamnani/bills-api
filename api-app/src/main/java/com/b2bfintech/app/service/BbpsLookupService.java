package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Biller lists and biller parameters, each cached for two hours. A scheduled clear drops both
 * so the next lookup calls the provider again.
 */
@Service
public class BbpsLookupService {

    public static final String BILLER_CACHE = "billers";
    public static final String PARAMETER_CACHE = "billerParameters";

    private final ObjectProvider<BbpsProvider> providers;
    private final CacheManager cacheManager;

    public BbpsLookupService(ObjectProvider<BbpsProvider> providers, CacheManager cacheManager) {
        this.providers = providers;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = BILLER_CACHE, key = "#categoryId + ':' + (#locationId == null ? '' : #locationId)")
    public List<BbpsProvider.ProviderBiller> billers(String categoryId, String locationId) {
        return List.copyOf(provider().fetchBillers(categoryId, locationId));
    }

    @Cacheable(cacheNames = PARAMETER_CACHE, key = "#categoryId + ':' + #billerId")
    public List<BbpsProvider.ProviderBillerParameter> parameters(String categoryId, String billerId) {
        return provider().fetchBillerParameters(categoryId, billerId);
    }

    @Scheduled(fixedDelayString = "${app.billing.kbill.parameter-cache-ttl-ms:7200000}",
            initialDelayString = "${app.billing.kbill.parameter-cache-ttl-ms:7200000}")
    public void evictParameters() {
        clear(BILLER_CACHE);
        clear(PARAMETER_CACHE);
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private BbpsProvider provider() {
        BbpsProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new BadRequestException("Bill provider is not configured");
        }
        return provider;
    }
}
