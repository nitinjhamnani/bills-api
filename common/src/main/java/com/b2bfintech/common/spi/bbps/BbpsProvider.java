package com.b2bfintech.common.spi.bbps;

import com.b2bfintech.common.spi.BillingProvider;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic BBPS catalog and bill-fetch port. Identifiers are opaque strings so a
 * different aggregator is a new implementation plus a re-sync, not a change to any caller.
 */
public interface BbpsProvider {

    String providerCode();

    List<ProviderCategory> fetchCategories();

    List<ProviderLocation> fetchLocations();

    List<ProviderBiller> fetchBillers(String categoryId, String locationId);

    List<ProviderBillerParameter> fetchBillerParameters(String categoryId, String billerId);

    BillingProvider.FetchedBill fetchBill(BillFetchCommand command);

    record ProviderCategory(String categoryId, String name, String group, boolean active) {
    }

    record ProviderLocation(String locationId, String name, String abbreviation) {
    }

    record ProviderBiller(String billerId, String name, String categoryId, String locationId, boolean active) {
    }

    /**
     * One input a customer must supply for a biller. The provider does not send a required flag
     * or an options list, so every declared parameter is treated as required.
     */
    record ProviderBillerParameter(
            String name, String label, String dataType, String validationRegex, String errorMessage, int order) {
    }

    record BillFetchCommand(
            String billerId,
            String categoryId,
            String accountNumber,
            Map<String, String> parameters,
            String customerMobileNumber,
            String senderName,
            String clientReferenceId) {
    }
}
