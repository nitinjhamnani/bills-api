package com.b2bfintech.billing.provider.kbill;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Maps KBill JSON onto the provider-agnostic records. Ids are kept as text, never parsed to int. */
public final class KBillMapper {

    private static final String[] AMOUNT_KEYS = {"bill_amount", "amount", "due_amount", "payable_amount", "billAmount"};
    private static final String[] DUE_DATE_KEYS = {"due_date", "dueDate", "bill_due_date"};
    private static final String[] BILL_DATE_KEYS = {"bill_date", "billDate"};
    private static final String[] BILL_NUMBER_KEYS = {"bill_number", "billNumber", "bill_no"};
    private static final String[] NAME_KEYS = {"customer_name", "customerName", "consumer_name", "name"};
    private static final String[] PERIOD_KEYS = {"bill_period", "billPeriod"};

    private KBillMapper() {
    }

    public static List<BbpsProvider.ProviderCategory> categories(JsonNode data) {
        List<BbpsProvider.ProviderCategory> result = new ArrayList<>();
        for (JsonNode node : array(data)) {
            result.add(new BbpsProvider.ProviderCategory(
                    idText(node, "operator_category_id"),
                    text(node, "operator_category_name"),
                    text(node, "operator_category_group"),
                    "1".equals(text(node, "status"))));
        }
        return result;
    }

    public static List<BbpsProvider.ProviderLocation> locations(JsonNode data) {
        List<BbpsProvider.ProviderLocation> result = new ArrayList<>();
        for (JsonNode node : array(data)) {
            result.add(new BbpsProvider.ProviderLocation(
                    idText(node, "operator_location_id"),
                    text(node, "operator_location_name"),
                    text(node, "abbreviation")));
        }
        return result;
    }

    public static List<BbpsProvider.ProviderBiller> billers(JsonNode data, String categoryId) {
        List<BbpsProvider.ProviderBiller> result = new ArrayList<>();
        for (JsonNode node : array(data)) {
            String billerId = idText(node, "operator_id");
            String locationId = normalizeLocation(idText(node, "location_id"));
            String ownCategory = idText(node, "operator_category");
            String name = text(node, "name");
            if (name == null) {
                name = "Biller " + billerId;
            }
            result.add(new BbpsProvider.ProviderBiller(
                    billerId,
                    name,
                    ownCategory == null || ownCategory.isBlank() ? categoryId : ownCategory,
                    locationId,
                    true));
        }
        return result;
    }

    /**
     * Keeps nationwide billers ({@code location_id} 0, stored as null) and billers for the location
     * the user picked. Comparison ignores leading zeros because the location master is zero-padded
     * ({@code "08"}) while operator payloads are not.
     */
    public static List<BbpsProvider.ProviderBiller> filterByLocation(
            List<BbpsProvider.ProviderBiller> billers, String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return billers;
        }
        return billers.stream()
                .filter(biller -> biller.locationId() == null || sameLocation(biller.locationId(), locationId))
                .toList();
    }

    public static List<BbpsProvider.ProviderBillerParameter> parameters(JsonNode data) {
        List<BbpsProvider.ProviderBillerParameter> result = new ArrayList<>();
        int index = 0;
        for (JsonNode node : array(data)) {
            String orderText = text(node, "param_id");
            int order = index;
            if (orderText != null) {
                try {
                    order = Integer.parseInt(orderText);
                } catch (NumberFormatException ignored) {
                    order = index;
                }
            }
            result.add(new BbpsProvider.ProviderBillerParameter(
                    text(node, "param_name"),
                    text(node, "param_label"),
                    text(node, "param_type"),
                    text(node, "regex"),
                    text(node, "error_message"),
                    order));
            index++;
        }
        return result;
    }

    public static BillingProvider.FetchedBill bill(JsonNode data) {
        JsonNode node = data != null && data.isArray() && !data.isEmpty() ? data.get(0) : data;
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BillFetchFailedException("Bill provider returned no bill fields", false);
        }
        String amountText = first(node, AMOUNT_KEYS);
        if (amountText == null) {
            throw new BillFetchFailedException(
                    "Bill provider response had no amount field. Keys present: " + fieldNames(node), false);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new BillFetchFailedException("Bill provider returned an unreadable amount: " + amountText, false);
        }
        return new BillingProvider.FetchedBill(
                first(node, NAME_KEYS),
                first(node, "utility_acc_no", "consumer_number", "consumerNumber"),
                amount,
                first(node, BILL_DATE_KEYS),
                first(node, DUE_DATE_KEYS),
                first(node, BILL_NUMBER_KEYS),
                first(node, PERIOD_KEYS),
                List.of());
    }

    static boolean sameLocation(String left, String right) {
        if (left == null || right == null) return false;
        if (left.equals(right)) return true;
        return stripLeadingZeros(left).equals(stripLeadingZeros(right));
    }

    /** {@code "0"} means nationwide and is stored as null. Any other id is kept verbatim. */
    static String normalizeLocation(String locationId) {
        if (locationId == null || locationId.isBlank() || "0".equals(stripLeadingZeros(locationId))) {
            return null;
        }
        return locationId;
    }

    private static String stripLeadingZeros(String value) {
        int i = 0;
        while (i < value.length() - 1 && value.charAt(i) == '0') {
            i++;
        }
        return value.substring(i);
    }

    private static List<JsonNode> array(JsonNode data) {
        List<JsonNode> nodes = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(nodes::add);
        }
        return nodes;
    }

    private static String idText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return "";
        return value.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String first(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (value != null) return value;
        }
        return null;
    }

    private static String fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        return String.join(", ", names);
    }
}
