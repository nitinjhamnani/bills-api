package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.model.CategoryOperator;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import com.b2bfintech.platform.service.CatalogCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bill creation reads operators and their fields from memory, then the database, and calls the
 * provider only when both are empty. Once a field list is in memory it stays there.
 */
@Service
public class BbpsLookupService {

    private static final TypeReference<List<BbpsProvider.ProviderBillerParameter>> PARAMETERS =
            new TypeReference<>() {
            };

    private final ObjectProvider<BbpsProvider> providers;
    private final BbpsCategoryRepository categoryRepository;
    private final CategoryOperatorRepository operatorRepository;
    private final CatalogCache catalogCache;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public BbpsLookupService(
            ObjectProvider<BbpsProvider> providers,
            BbpsCategoryRepository categoryRepository,
            CategoryOperatorRepository operatorRepository,
            CatalogCache catalogCache,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.categoryRepository = categoryRepository;
        this.operatorRepository = operatorRepository;
        this.catalogCache = catalogCache;
        this.objectMapper = objectMapper;
    }

    public List<BbpsProvider.ProviderBiller> billers(String categoryId) {
        List<BbpsProvider.ProviderBiller> cached = catalogCache.operators(categoryId);
        if (cached != null) {
            return cached;
        }
        synchronized (lock("operators:" + categoryId)) {
            cached = catalogCache.operators(categoryId);
            if (cached != null) {
                return cached;
            }
            BbpsCategory category = requireCategory(categoryId);
            List<CategoryOperator> stored = operatorRepository.findActiveByCategory(category.id());
            if (!stored.isEmpty()) {
                rememberParameters(stored);
                List<BbpsProvider.ProviderBiller> operators = stored.stream()
                        .map(operator -> toBiller(operator, categoryId)).toList();
                catalogCache.putOperators(categoryId, operators);
                return operators;
            }
            List<BbpsProvider.ProviderBiller> fetched = List.copyOf(provider().fetchBillers(categoryId));
            if (fetched.isEmpty()) {
                return fetched;
            }
            try {
                operatorRepository.insertAll(category.id(), category.providerCode(), fetched);
            } catch (DataIntegrityViolationException ex) {
                stored = operatorRepository.findActiveByCategory(category.id());
                rememberParameters(stored);
                List<BbpsProvider.ProviderBiller> operators = stored.stream()
                        .map(operator -> toBiller(operator, categoryId)).toList();
                catalogCache.putOperators(categoryId, operators);
                return operators;
            }
            catalogCache.putOperators(categoryId, fetched);
            return fetched;
        }
    }

    public List<BbpsProvider.ProviderBillerParameter> parameters(String categoryId, String billerId) {
        if (catalogCache.hasParameters(billerId)) {
            return catalogCache.parameters(billerId);
        }
        synchronized (lock("params:" + billerId)) {
            if (catalogCache.hasParameters(billerId)) {
                return catalogCache.parameters(billerId);
            }
            CategoryOperator stored = operatorRepository.findActiveByOperatorId(billerId).orElse(null);
            if (stored != null && stored.operatorParams() != null) {
                List<BbpsProvider.ProviderBillerParameter> parameters = readParameters(stored.operatorParams());
                catalogCache.putParameters(billerId, parameters);
                return parameters;
            }
            requireCategory(categoryId);
            List<BbpsProvider.ProviderBillerParameter> fetched =
                    List.copyOf(provider().fetchBillerParameters(categoryId, billerId));
            if (stored != null) {
                operatorRepository.saveParams(billerId, writeParameters(fetched));
            }
            catalogCache.putParameters(billerId, fetched);
            return fetched;
        }
    }

    private void rememberParameters(List<CategoryOperator> stored) {
        for (CategoryOperator operator : stored) {
            if (operator.operatorParams() != null && !catalogCache.hasParameters(operator.operatorId())) {
                catalogCache.putParameters(operator.operatorId(), readParameters(operator.operatorParams()));
            }
        }
    }

    private BbpsProvider.ProviderBiller toBiller(CategoryOperator operator, String categoryId) {
        return new BbpsProvider.ProviderBiller(operator.operatorId(), operator.operatorName(), categoryId, operator.active());
    }

    private BbpsCategory requireCategory(String categoryId) {
        return catalogCache.category(categoryId)
                .or(() -> categoryRepository.findActiveByCategoryId(categoryId))
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryId));
    }

    private String writeParameters(List<BbpsProvider.ProviderBillerParameter> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store operator parameters", ex);
        }
    }

    private List<BbpsProvider.ProviderBillerParameter> readParameters(String json) {
        try {
            return objectMapper.readValue(json, PARAMETERS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read operator parameters", ex);
        }
    }

    private Object lock(String key) {
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private BbpsProvider provider() {
        BbpsProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new BadRequestException("Bill provider is not configured");
        }
        return provider;
    }
}
