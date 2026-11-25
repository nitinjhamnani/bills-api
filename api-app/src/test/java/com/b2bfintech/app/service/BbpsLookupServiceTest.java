package com.b2bfintech.app.service;

import com.b2bfintech.app.config.BillingCatalogProperties;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.model.CategoryOperator;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import com.b2bfintech.platform.service.CatalogCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BbpsLookupServiceTest {

    private static final UUID CATEGORY_ROW = UUID.randomUUID();

    private BbpsProvider provider;
    private CategoryOperatorRepository operatorRepository;
    private BbpsLookupService service;

    @BeforeEach
    void setUp() {
        provider = mock(BbpsProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BbpsProvider> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(provider);

        BbpsCategoryRepository categoryRepository = mock(BbpsCategoryRepository.class);
        operatorRepository = mock(CategoryOperatorRepository.class);
        when(categoryRepository.findActiveByCategoryId("Mobile Postpaid")).thenReturn(Optional.of(
                new BbpsCategory(CATEGORY_ROW, "BRANCHX", "Mobile Postpaid", "Mobile Postpaid", "fetch", true, Instant.now())));

        BillingCatalogProperties properties = new BillingCatalogProperties();
        properties.setExcludedOperatorIds(List.of("ATPOST000NAT01"));

        service = new BbpsLookupService(
                providers, categoryRepository, operatorRepository, new CatalogCache(3_600_000), properties, new ObjectMapper());
    }

    @Test
    void excludedOperatorIsNotSavedOnFirstLoad() {
        when(operatorRepository.existsByCategoryId(CATEGORY_ROW)).thenReturn(false);
        when(provider.fetchBillers("Mobile Postpaid")).thenReturn(List.of(
                new BbpsProvider.ProviderBiller("ATPOST000NAT01", "Airtel Postpaid", "Mobile Postpaid", true),
                new BbpsProvider.ProviderBiller("ATPOST000NAT02", "Airtel Postpaid (Fetch and Pay)", "Mobile Postpaid", true)));

        List<BbpsProvider.ProviderBiller> loaded = service.billers("Mobile Postpaid");

        assertEquals(List.of("ATPOST000NAT02"), loaded.stream().map(BbpsProvider.ProviderBiller::billerId).toList());
        verify(operatorRepository).insertAll(eq(CATEGORY_ROW), eq("BRANCHX"), eq(List.of(
                new BbpsProvider.ProviderBiller("ATPOST000NAT02", "Airtel Postpaid (Fetch and Pay)", "Mobile Postpaid", true))));
    }

    @Test
    void excludedOperatorAlreadyStoredIsNotLoaded() {
        when(operatorRepository.existsByCategoryId(CATEGORY_ROW)).thenReturn(true);
        when(operatorRepository.findActiveByCategory(CATEGORY_ROW)).thenReturn(List.of(
                operator("ATPOST000NAT01", "Airtel Postpaid"),
                operator("ATPOST000NAT02", "Airtel Postpaid (Fetch and Pay)")));

        List<BbpsProvider.ProviderBiller> loaded = service.billers("Mobile Postpaid");

        assertEquals(List.of("ATPOST000NAT02"), loaded.stream().map(BbpsProvider.ProviderBiller::billerId).toList());
        verify(provider, never()).fetchBillers(any());
        verify(operatorRepository, never()).insertAll(any(), any(), any());
    }

    private static CategoryOperator operator(String id, String name) {
        return new CategoryOperator(UUID.randomUUID(), name, id, "BRANCHX", null, null, CATEGORY_ROW, true);
    }
}
