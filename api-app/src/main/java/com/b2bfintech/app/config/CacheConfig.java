package com.b2bfintech.app.config;

import com.b2bfintech.app.service.BbpsLookupService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(BbpsLookupService.BILLER_CACHE, BbpsLookupService.PARAMETER_CACHE);
    }
}
