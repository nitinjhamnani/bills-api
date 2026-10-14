package com.b2bfintech.billing.provider.kbill;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.common.spi.bbps.BillProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "kbill")
public class KBillBbpsProvider implements BbpsProvider, BillingProvider {

    public static final String PROVIDER_CODE = "KBILL";

    private final RestClient client;
    private final KBillProperties properties;
    private final ObjectMapper objectMapper;

    public KBillBbpsProvider(RestClient kbillRestClient, KBillProperties properties, ObjectMapper objectMapper) {
        this.client = kbillRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public List<ProviderCategory> fetchCategories() {
        return KBillMapper.categories(get(uri(properties.getPaths().getCategory()), false));
    }

    @Override
    public List<ProviderLocation> fetchLocations() {
        return KBillMapper.locations(get(uri(properties.getPaths().getLocation()), false));
    }

    @Override
    public List<ProviderBiller> fetchBillers(String categoryId, String locationId) {
        String queryLocation = locationId == null || locationId.isBlank() ? null : queryLocationId(locationId);
        List<ProviderBiller> scoped = loadBillers(categoryId, queryLocation);
        List<ProviderBiller> matched = KBillMapper.filterByLocation(scoped, locationId);
        if (!matched.isEmpty() || queryLocation == null || "0".equals(queryLocation)) {
            return matched;
        }
        // A location-scoped call can come back empty for a category that still has billers.
        // operator_location_id=0 is the full list for every category that accepts it.
        try {
            List<ProviderBiller> all = loadBillers(categoryId, "0");
            return chooseBillers(all, locationId);
        } catch (BillProviderException ex) {
            return matched;
        }
    }

    /**
     * Prefer billers for the selected location, including nationwide ones. When the provider has
     * billers for the category but none for that location, return the category list so the caller
     * is not shown an empty catalog.
     */
    static List<ProviderBiller> chooseBillers(List<ProviderBiller> categoryWide, String locationId) {
        List<ProviderBiller> matched = KBillMapper.filterByLocation(categoryWide, locationId);
        if (!matched.isEmpty()) {
            return matched;
        }
        return categoryWide == null ? List.of() : categoryWide;
    }

    private List<ProviderBiller> loadBillers(String categoryId, String queryLocation) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(properties.getPaths().getOperatorList())
                .queryParam("category_id", categoryId);
        if (queryLocation != null && !queryLocation.isBlank()) {
            // KBill rejects a zero-padded id ("07") as not an integer. Our ids stay padded.
            builder.queryParam("operator_location_id", queryLocation);
        }
        return KBillMapper.billers(get(absolute(builder), false), categoryId);
    }

    @Override
    public List<ProviderBillerParameter> fetchBillerParameters(String categoryId, String billerId) {
        URI uri = absolute(UriComponentsBuilder.fromPath(properties.getPaths().getOperatorParameters())
                .queryParam("operator_id", billerId)
                .queryParam("category_id", categoryId));
        return KBillMapper.parameters(get(uri, false));
    }

    @Override
    public FetchedBill fetchBill(BillFetchCommand command) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(properties.getPaths().getBillFetch())
                .queryParam("operator_id", nullToEmpty(command.billerId()))
                .queryParam("utility_acc_no", nullToEmpty(command.accountNumber()))
                .queryParam("category", nullToEmpty(command.categoryId()))
                .queryParam("client_ref_id", nullToEmpty(command.clientReferenceId()))
                .queryParam("mobile_no", nullToEmpty(command.customerMobileNumber()))
                .queryParam("sender_name", nullToEmpty(command.senderName()));
        if (command.parameters() != null) {
            command.parameters().forEach((name, value) -> {
                if ("utility_acc_no".equals(name)) return;
                builder.queryParam(name, value == null ? "" : value);
            });
        }
        return KBillMapper.bill(get(absolute(builder), true));
    }

    @Override
    public PaymentResult pay(String idempotencyKey, String billServiceCode, String customerBillAccountNumber, java.math.BigDecimal amount) {
        throw new UnsupportedOperationException("KBill bill payment is not wired; organisation settlement stays manual");
    }

    private JsonNode get(URI uri, boolean billFetch) {
        try {
            return client.get().uri(uri).exchange((request, response) -> {
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                int httpStatus = response.getStatusCode().value();
                JsonNode body = parse(raw, httpStatus, billFetch);
                try {
                    return KBillEnvelopeReader.payload(body, httpStatus);
                } catch (KBillCallException ex) {
                    throw translate(ex, billFetch);
                }
            });
        } catch (ResourceAccessException ex) {
            if (billFetch) {
                throw new BillFetchFailedException("Bill provider did not respond: " + ex.getMessage(), false);
            }
            throw new BillProviderException("Bill provider did not respond: " + ex.getMessage(), false, null);
        }
    }

    private JsonNode parse(String raw, int httpStatus, boolean billFetch) {
        if (raw == null || raw.isBlank()) {
            throw translate(new KBillCallException("Bill provider returned an empty response", null, false, httpStatus), billFetch);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            String snippet = raw.length() > 200 ? raw.substring(0, 200) : raw;
            throw translate(new KBillCallException(
                    "Bill provider returned a non-JSON response: " + snippet, null, false, httpStatus), billFetch);
        }
    }

    private RuntimeException translate(KBillCallException ex, boolean billFetch) {
        if (billFetch) {
            return new BillFetchFailedException(ex.getMessage(), ex.isPermanent());
        }
        return new BillProviderException(ex.getMessage(), ex.isPermanent(), ex.responseCode());
    }

    private URI uri(String path) {
        return absolute(properties.getBaseUrl(), UriComponentsBuilder.fromPath(path));
    }

    private URI absolute(UriComponentsBuilder builder) {
        return absolute(properties.getBaseUrl(), builder);
    }

    static String queryLocationId(String locationId) {
        String trimmed = locationId.trim();
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            return trimmed;
        }
        return trimmed.replaceFirst("^0+(?!$)", "");
    }

    static URI absolute(String baseUrl, UriComponentsBuilder relative) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        org.springframework.web.util.UriComponents parts = relative.build();
        return UriComponentsBuilder.fromUriString(base)
                .path(parts.getPath() == null ? "" : parts.getPath())
                .replaceQueryParams(parts.getQueryParams())
                .build()
                .encode()
                .toUri();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
