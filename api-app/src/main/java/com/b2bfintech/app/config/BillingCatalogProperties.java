package com.b2bfintech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Operators left out of the catalog. They are not stored and are not returned on later reads. */
@Component
@ConfigurationProperties(prefix = "app.billing")
public class BillingCatalogProperties {

    private List<String> excludedOperatorIds = new ArrayList<>();

    public boolean excludes(String operatorId) {
        return operatorId != null && excludedOperatorIds.contains(operatorId);
    }

    public List<String> getExcludedOperatorIds() {
        return excludedOperatorIds;
    }

    public void setExcludedOperatorIds(List<String> excludedOperatorIds) {
        this.excludedOperatorIds = excludedOperatorIds == null ? new ArrayList<>() : excludedOperatorIds;
    }
}
