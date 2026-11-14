package com.b2bfintech.billing.provider.branchx;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.BillingProvider.HttpBillFetch;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 10X FI catalogue and bill fetch. Payment, QuickPay, status polling, and complaints stay out
 * until a later pass. Only categories that publish a bill are listed.
 */
@Component
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "branchx")
public class BranchXBbpsProvider implements BbpsProvider, BillingProvider {

    public static final String PROVIDER_CODE = "BRANCHX";

    private static final Logger log = LoggerFactory.getLogger(BranchXBbpsProvider.class);

    /** Category name to the provider_type_id values that return it. Insurance is split across three. */
    static final Map<String, List<String>> FETCH_CATEGORIES = new LinkedHashMap<>();

    static {
        FETCH_CATEGORIES.put("Electricity", List.of("13"));
        FETCH_CATEGORIES.put("Gas", List.of("28"));
        FETCH_CATEGORIES.put("LPG Gas", List.of("44"));
        FETCH_CATEGORIES.put("Water", List.of("30"));
        FETCH_CATEGORIES.put("Mobile Postpaid", List.of("31"));
        FETCH_CATEGORIES.put("Broadband Postpaid", List.of("33"));
        FETCH_CATEGORIES.put("Landline Postpaid", List.of("32"));
        FETCH_CATEGORIES.put("Insurance", List.of("29", "40", "47"));
        FETCH_CATEGORIES.put("Loan Repayment", List.of("35"));
        FETCH_CATEGORIES.put("Credit Card", List.of("41"));
        FETCH_CATEGORIES.put("Fastag", List.of("37"));
        FETCH_CATEGORIES.put("Education Fees", List.of("34"));
        FETCH_CATEGORIES.put("Municipal Taxes", List.of("39"));
    }

    private final RestClient client;
    private final ObjectMapper json;

    public BranchXBbpsProvider(RestClient branchxRestClient, ObjectMapper json) {
        this.client = branchxRestClient;
        this.json = json;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public List<ProviderCategory> fetchCategories() {
        List<ProviderCategory> categories = new ArrayList<>();
        for (String name : FETCH_CATEGORIES.keySet()) {
            categories.add(new ProviderCategory(name, name, "fetch", true));
        }
        return categories;
    }

    @Override
    public List<ProviderBiller> fetchBillers(String categoryId) {
        List<String> typeIds = FETCH_CATEGORIES.get(categoryId);
        if (typeIds == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ProviderBiller> billers = new ArrayList<>();
        for (String typeId : typeIds) {
            JsonNode body = get("/BillPayment/operators", Map.of("provider_type_id", typeId));
            for (ProviderBiller biller : BranchXMapper.billers(body, categoryId)) {
                if (seen.add(biller.billerId())) {
                    billers.add(biller);
                }
            }
        }
        billers.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return billers;
    }

    @Override
    public List<ProviderBillerParameter> fetchBillerParameters(String categoryId, String billerId) {
        JsonNode body = get("/BillPayment/operator/params", Map.of("external_reference_id", billerId));
        return BranchXMapper.parameters(body);
    }

    @Override
    public FetchedBill fetchBill(BillFetchCommand command) {
        HttpBillFetch http = fetchBillHttp(command);
        if (http.statusCode() != 200) {
            throw new BillFetchFailedException("Bill provider returned HTTP " + http.statusCode(), false);
        }
        try {
            return BranchXMapper.bill(json.readTree(http.body() == null ? "" : http.body()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BillFetchFailedException("Bill provider returned a response that could not be read", false);
        }
    }

    @Override
    public HttpBillFetch fetchBillHttp(BillFetchCommand command) {
        ObjectNode payload = json.createObjectNode();
        payload.put("RequestID", BranchXMapper.requestId(command.clientReferenceId()));
        payload.put("UserMobileNumber", command.customerMobileNumber() == null ? "" : command.customerMobileNumber());
        payload.put("BillerCode", command.billerId() == null ? "" : command.billerId());
        payload.put("Category", command.categoryId() == null ? "" : command.categoryId());
        ArrayNode inputs = payload.putArray("InputParams");
        if (command.parameters() != null) {
            command.parameters().forEach((name, value) -> {
                if (name == null || value == null || value.isBlank()) {
                    return;
                }
                BranchXMapper.addInput(inputs, name, value);
            });
        }
        return postRaw("/BillPayment/bill/fetch", payload);
    }

    @Override
    public PaymentResult pay(String idempotencyKey, String billServiceCode, String customerBillAccountNumber, BigDecimal amount) {
        throw new UnsupportedOperationException("10X bill payment is not wired yet");
    }

    private JsonNode get(String path, Map<String, String> query) {
        ExchangeResult result = exchange(HttpMethod.GET, path, query, null);
        if (result.body().isBlank()) {
            throw new BillFetchFailedException("Bill provider returned an empty response", false);
        }
        try {
            return json.readTree(result.body());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BillFetchFailedException("Bill provider returned a response that could not be read", false);
        }
    }

    private HttpBillFetch postRaw(String path, JsonNode payload) {
        ExchangeResult result = exchange(HttpMethod.POST, path, Map.of(), payload);
        return new HttpBillFetch(result.status(), result.body());
    }

    private ExchangeResult exchange(HttpMethod method, String path, Map<String, String> query, JsonNode payload) {
        long started = System.nanoTime();
        String loggedPath = pathWithQuery(path, query);
        String requestBody = payload == null ? "" : redact(payload.toString());
        try {
            var spec = client.method(method).uri(builder -> {
                builder.path(path);
                if (query != null) {
                    query.forEach(builder::queryParam);
                }
                return builder.build();
            });
            if (payload != null) {
                spec = spec.body(payload);
            }
            return spec.exchange((request, response) -> {
                String responseBody = new String(
                        response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                int status = response.getStatusCode().value();
                log.info("provider=branchx method={} path={} status={} durationMs={} request={} response={}",
                        method.name(), loggedPath, status,
                        (System.nanoTime() - started) / 1_000_000,
                        requestBody, redact(responseBody));
                return new ExchangeResult(status, responseBody);
            });
        } catch (RestClientException ex) {
            log.info("provider=branchx method={} path={} status=0 durationMs={} request={} response=unreachable",
                    method.name(), loggedPath, (System.nanoTime() - started) / 1_000_000, requestBody);
            throw new BillFetchFailedException("Bill provider could not be reached", false);
        }
    }

    private record ExchangeResult(int status, String body) {
    }

    private static String pathWithQuery(String path, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return path;
        }
        StringBuilder builder = new StringBuilder(path).append('?');
        query.forEach((key, value) -> builder.append(key).append('=').append(value).append('&'));
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    /** Drops API keys and tokens if a provider echoes them in a body. */
    private static String redact(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body
                .replaceAll("(?i)(\"(?:apiToken|api_token|apiKey|api_key|token|authorization)\"\\s*:\\s*\")[^\"]*\"", "$1[redacted]\"")
                .replaceAll("(?i)((?:apiToken|api_token|apiKey|api_key|token)=)[^&\\s]+", "$1[redacted]");
    }
}
