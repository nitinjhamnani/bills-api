package com.b2bfintech.platform.service;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Categories, operators, and operator fields kept in memory for bill creation with configurable TTL. */
@Component
public class CatalogCache {

    private record Timed<T>(T value, long expiresAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }

    private final long ttlMillis;
    private volatile List<BbpsCategory> categories = List.of();
    private final ConcurrentHashMap<String, Timed<List<BbpsProvider.ProviderBiller>>> operators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timed<List<BbpsProvider.ProviderBillerParameter>>> parameters = new ConcurrentHashMap<>();

    public CatalogCache(@Value("${app.billing.parameter-cache-ttl-ms:7200000}") long ttlMillis) {
        this.ttlMillis = Math.max(60_000L, ttlMillis);
    }

    public void replaceCategories(List<BbpsCategory> next) {
        categories = List.copyOf(next);
    }

    public List<BbpsCategory> categories() {
        return categories;
    }

    public Optional<BbpsCategory> category(String categoryId) {
        return categories.stream()
                .filter(category -> category.active() && categoryId.equals(category.categoryId()))
                .findFirst();
    }

    public List<BbpsProvider.ProviderBiller> operators(String categoryId) {
        Timed<List<BbpsProvider.ProviderBiller>> entry = operators.get(categoryId);
        if (entry == null) return null;
        if (entry.expired()) {
            operators.remove(categoryId, entry);
            return null;
        }
        return entry.value();
    }

    public void putOperators(String categoryId, List<BbpsProvider.ProviderBiller> billers) {
        operators.put(categoryId, new Timed<>(List.copyOf(billers), System.currentTimeMillis() + ttlMillis));
    }

    public void evictOperators(String categoryId) {
        operators.remove(categoryId);
    }

    public boolean hasParameters(String operatorId) {
        Timed<List<BbpsProvider.ProviderBillerParameter>> entry = parameters.get(operatorId);
        if (entry == null) return false;
        if (entry.expired()) {
            parameters.remove(operatorId, entry);
            return false;
        }
        return true;
    }

    public List<BbpsProvider.ProviderBillerParameter> parameters(String operatorId) {
        Timed<List<BbpsProvider.ProviderBillerParameter>> found = parameters.get(operatorId);
        if (found == null || found.expired()) {
            if (found != null) parameters.remove(operatorId, found);
            return List.of();
        }
        return found.value();
    }

    public void putParameters(String operatorId, List<BbpsProvider.ProviderBillerParameter> values) {
        parameters.put(operatorId, new Timed<>(List.copyOf(values), System.currentTimeMillis() + ttlMillis));
    }
}
