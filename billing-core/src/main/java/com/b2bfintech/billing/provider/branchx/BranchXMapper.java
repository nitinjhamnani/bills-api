package com.b2bfintech.billing.provider.branchx;

import com.b2bfintech.common.spi.BillFetchFailedException;
import com.b2bfintech.common.spi.BillingProvider;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

/** Maps 10X FI JSON onto the provider-agnostic records. Biller ids stay text. */
public final class BranchXMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] AMOUNT_KEYS = {
            "billAmount", "bill_amount", "amount", "dueAmount", "due_amount", "payableAmount", "netBillAmount",
            "LastPayedAmount", "LastPaidAmount"};
    private static final String[] DUE_DATE_KEYS = {"dueDate", "DueDate", "billDueDate", "due_date", "bill_due_date"};
    private static final String[] BILL_DATE_KEYS = {"billDate", "BillDate", "billdate", "bill_date"};
    private static final String[] BILL_NUMBER_KEYS = {"billNumber", "bill_number", "billNo"};
    private static final String[] NAME_KEYS = {
            "customerName", "customer_name", "consumerName", "ConsumerReferenceName", "utilitycustomername"};
    private static final String[] PERIOD_KEYS = {"billPeriod", "Billperiod", "billperiod", "bill_period"};
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DASHED_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private BranchXMapper() {
    }

    public static List<BbpsProvider.ProviderBiller> billers(JsonNode data, String categoryId) {
        List<BbpsProvider.ProviderBiller> result = new ArrayList<>();
        for (JsonNode node : array(operators(data))) {
            if (!node.path("activeStatus").asBoolean(true)) {
                continue;
            }
            String category = text(node, "category");
            if (categoryId != null && !categoryId.equals(category)) {
                continue;
            }
            String billerId = text(node, "externalReferenceNumber");
            String name = text(node, "name");
            if (billerId == null || name == null) {
                continue;
            }
            result.add(new BbpsProvider.ProviderBiller(billerId, name, category, true));
        }
        return result;
    }

    public static List<BbpsProvider.ProviderBillerParameter> parameters(JsonNode body) {
        JsonNode data = unwrap(body);
        JsonNode list = data.get("billDetailsParams");
        if (list == null) {
            list = data.get("BillDetailsParams");
        }
        List<BbpsProvider.ProviderBillerParameter> result = new ArrayList<>();
        int index = 0;
        for (JsonNode node : array(list)) {
            String name = text(node, "paramName");
            if (name == null) {
                name = text(node, "name");
            }
            if (name == null) {
                continue;
            }
            String label = text(node, "name");
            result.add(new BbpsProvider.ProviderBillerParameter(
                    name,
                    label == null ? name : label,
                    text(node, "dataType"),
                    text(node, "regex"),
                    lengthHint(node),
                    index));
            index++;
        }
        return result;
    }

    public static BillingProvider.FetchedBill bill(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw new BillFetchFailedException("Bill provider returned no bill fields", false);
        }
        if (!success(body)) {
            throw new BillFetchFailedException(failureMessage(body), permanent(body));
        }
        JsonNode data = unwrap(body);
        JsonNode decoded = decodedBill(data);
        JsonNode biller = firstObject(data, "billerResponse", "BillerResponse");
        JsonNode amountInfo = firstObject(data, "amountInfo", "AmountInfo");
        String amountText = first(biller, amountInfo, decoded, data, AMOUNT_KEYS);
        if (amountText == null) {
            throw new BillFetchFailedException(
                    "Bill provider response had no amount field. Keys present: " + fieldNames(data), false);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new BillFetchFailedException("Bill provider returned an unreadable amount: " + amountText, false);
        }
        return new BillingProvider.FetchedBill(
                first(biller, decoded, data, NAME_KEYS),
                first(biller, decoded, data, "customerId", "customer_id", "consumerNumber"),
                amount,
                normalizeDate(first(biller, decoded, data, BILL_DATE_KEYS)),
                normalizeDate(first(biller, decoded, data, DUE_DATE_KEYS)),
                first(biller, decoded, data, BILL_NUMBER_KEYS),
                first(biller, decoded, data, PERIOD_KEYS),
                List.of(),
                text(data, "billFetchBase64"));
    }

    /** 10X reads the account from paramValue. The label stays paramName. */
    static void addInput(ArrayNode inputs, String name, String value) {
        ObjectNode item = inputs.addObject();
        item.put("paramName", name);
        item.put("paramValue", value);
    }

    /** 10X keeps 35 alphanumeric characters of the merchant reference. */
    static String requestId(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            cleaned = "PTX" + Long.toUnsignedString(System.currentTimeMillis(), 36).toUpperCase();
        }
        return cleaned.length() > 35 ? cleaned.substring(0, 35) : cleaned;
    }

    static String normalizeDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.matches("\\d{8}")) {
                return LocalDate.parse(value, BASIC_DATE).toString();
            }
            if (value.matches("\\d{2}-\\d{2}-\\d{4}")) {
                return LocalDate.parse(value, DASHED_DATE).toString();
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value).toString();
            }
            if (value.length() >= 19 && value.charAt(10) == 'T') {
                return LocalDate.parse(value.substring(0, 10)).toString();
            }
        } catch (DateTimeParseException ex) {
            return null;
        }
        return value;
    }

    private static JsonNode operators(JsonNode body) {
        JsonNode data = unwrap(body);
        return data.isArray() ? data : data.get("data");
    }

    private static JsonNode unwrap(JsonNode body) {
        if (body != null && body.isObject() && body.has("data") && !body.get("data").isNull()) {
            return body.get("data");
        }
        return body == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : body;
    }

    private static boolean success(JsonNode body) {
        JsonNode status = body.get("status");
        if (status != null && status.isBoolean()) {
            return status.asBoolean();
        }
        if (status != null && "SUCCESS".equalsIgnoreCase(status.asText())) {
            String code = text(body, "statusCode");
            return code == null || "200".equals(code);
        }
        return false;
    }

    static String failureMessage(JsonNode body) {
        String message = text(body, "message");
        if (message == null) {
            message = text(body, "title");
        }
        if (message != null && message.toLowerCase().contains("invalid ip")) {
            return "Bill provider rejected this server's IP. " + message;
        }
        return message == null ? "Bill provider call failed" : message;
    }

    /** A bad account is permanent. An IP or availability problem is tried again. */
    private static boolean permanent(JsonNode body) {
        String message = failureMessage(body);
        String lower = message.toLowerCase();
        return lower.contains("invalid") && !lower.contains("invalid ip");
    }

    private static String lengthHint(JsonNode node) {
        String min = text(node, "minLength");
        String max = text(node, "maxLength");
        if (min == null && max == null) {
            return null;
        }
        if (min != null && min.equals(max)) {
            return "Enter " + min + " characters";
        }
        if (min != null && max != null) {
            return "Enter " + min + " to " + max + " characters";
        }
        return max != null ? "Enter at most " + max + " characters" : "Enter at least " + min + " characters";
    }

    private static JsonNode firstObject(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    private static String first(JsonNode primary, JsonNode secondary, String... keys) {
        return first(primary, secondary, null, keys);
    }

    private static String first(JsonNode primary, JsonNode secondary, JsonNode tertiary, String... keys) {
        return first(primary, secondary, tertiary, null, keys);
    }

    private static String first(
            JsonNode primary, JsonNode secondary, JsonNode tertiary, JsonNode quaternary, String... keys) {
        String value = first(primary, keys);
        if (value != null) {
            return value;
        }
        value = first(secondary, keys);
        if (value != null) {
            return value;
        }
        value = first(tertiary, keys);
        if (value != null) {
            return value;
        }
        return first(quaternary, keys);
    }

    /** The bill fields travel inside billFetchBase64. The original text is kept for a later payment. */
    private static JsonNode decodedBill(JsonNode data) {
        String encoded = text(data, "billFetchBase64");
        if (encoded == null) {
            return null;
        }
        try {
            JsonNode parsed = JSON.readTree(Base64.getDecoder().decode(encoded));
            return parsed.isObject() ? parsed : null;
        } catch (IllegalArgumentException | java.io.IOException ex) {
            return null;
        }
    }

    private static String first(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            String value = present(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String present(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("NA")) {
            return null;
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static List<JsonNode> array(JsonNode data) {
        List<JsonNode> nodes = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(nodes::add);
        }
        return nodes;
    }

    private static String fieldNames(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        return String.join(", ", names);
    }
}
