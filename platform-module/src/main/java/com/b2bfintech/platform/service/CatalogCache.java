package com.b2bfintech.platform.service;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Categories, operators, and operator fields kept in memory for bill creation. */
@Component
public class CatalogCache {

    private volatile List<BbpsCategory> categories = List.of();
    private final ConcurrentHashMap<String, List<BbpsProvider.ProviderBiller>> operators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<BbpsProvider.ProviderBillerParameter>> parameters = new ConcurrentHashMap<>();

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
        return operators.get(categoryId);
    }

    public void putOperators(String categoryId, List<BbpsProvider.ProviderBiller> billers) {
        operators.put(categoryId, List.copyOf(billers));
    }

    public boolean hasParameters(String operatorId) {
        return parameters.containsKey(operatorId);
    }

    public List<BbpsProvider.ProviderBillerParameter> parameters(String operatorId) {
        List<BbpsProvider.ProviderBillerParameter> found = parameters.get(operatorId);
        return found == null ? List.of() : found;
    }

    public void putParameters(String operatorId, List<BbpsProvider.ProviderBillerParameter> values) {
        parameters.put(operatorId, List.copyOf(values));
    }
}
