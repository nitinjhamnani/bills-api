package com.b2bfintech.billing.provider.branchx;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        return BranchXMapper.bill(post("/BillPayment/bill/fetch", payload));
    }

    @Override
    public PaymentResult pay(String idempotencyKey, String billServiceCode, String customerBillAccountNumber, BigDecimal amount) {
        throw new UnsupportedOperationException("10X bill payment is not wired yet");
    }

    private JsonNode get(String path, Map<String, String> query) {
        try {
            JsonNode body = client.get()
                    .uri(builder -> {
                        builder.path(path);
                        query.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new BillFetchFailedException("Bill provider returned an empty response", false);
            }
            return body;
        } catch (BillFetchFailedException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BillFetchFailedException("Bill provider could not be reached", false);
        }
    }

    private JsonNode post(String path, JsonNode payload) {
        try {
            JsonNode body = client.post().uri(path).body(payload).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new BillFetchFailedException("Bill provider returned an empty response", false);
            }
            return body;
        } catch (BillFetchFailedException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BillFetchFailedException("Bill provider could not be reached", false);
        }
    }
}
