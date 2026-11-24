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
    private static final String[] REFERENCE_KEYS = {
            "transactionId", "txnId", "transactionReference", "transactionRef", "referenceId",
            "referenceNumber", "refId", "bbpsRefId", "bbpsTxnId", "paymentId", "txnRefId", "approvalRefNum"
    };
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
            if (nothingDue(body)) {
                return new BillingProvider.FetchedBill(
                        null, null, BigDecimal.ZERO, null, null, null, null, List.of(), null);
            }
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

    /** The pay token is the original billFetchBase64 text. It is not decoded or trimmed. */
    public static String payToken(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        String token = text(unwrap(body), "billFetchBase64");
        if (token == null) {
            token = text(body, "billFetchBase64");
        }
        return token;
    }

    /** Amount, mobile, and the unchanged token. The customer name is sent only when it is present. */
    public static ObjectNode paymentRequest(ObjectMapper json, BillingProvider.FetchedBillSettlement settlement) {
        ObjectNode payload = json.createObjectNode();
        payload.put("amount", settlement.amount());
        payload.put("userMobileNumber", settlement.userMobileNumber() == null ? "" : settlement.userMobileNumber());
        payload.put("billFetchBase64", settlement.billFetchBase64() == null ? "" : settlement.billFetchBase64());
        if (settlement.userName() != null && !settlement.userName().isBlank()) {
            payload.put("userName", settlement.userName().trim());
        }
        return payload;
    }

    /**
     * HTTP 200 and status SUCCESS is a paid bill. FAILED, any other status, and HTTP 500 stay unpaid.
     * A short id is taken from data when one of the usual reference fields is present.
     */
    public static BillingProvider.SettlementResult paymentResult(int httpStatus, JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            return new BillingProvider.SettlementResult(
                    false, "Bill provider returned a response that could not be read", null, null);
        }
        boolean paid = httpStatus == 200 && "SUCCESS".equalsIgnoreCase(text(body, "status"));
        String message = text(body, "message");
        if (message == null) {
            message = text(body, "title");
        }
        if (!paid) {
            return new BillingProvider.SettlementResult(
                    false, message == null ? "Bill provider did not accept the payment" : message, null, dataText(body));
        }
        return new BillingProvider.SettlementResult(true, message, reference(unwrap(body)), dataText(body));
    }

    /**
     * A SUCCESS body with a numeric balance can be spent. FAILED, PENDING, and a missing balance cannot.
     * The returned message keeps the provider wording and status code.
     */
    public static BillingProvider.WalletBalance walletBalance(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            return unreadBalance(null, null);
        }
        String status = text(body, "status");
        String statusCode = text(body, "statusCode");
        String message = text(body, "message");
        BigDecimal balance = decimal(body.get("balance"));
        boolean readable = "SUCCESS".equalsIgnoreCase(status) && balance != null;
        return new BillingProvider.WalletBalance(
                readable, readable ? balance : null, status, statusCode, providerNotice(message, statusCode));
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

    /**
     * BillAvenue reports a paid period as FAILED with BFR004. The same wording, without the code,
     * is the same outcome. Every other failure stays a failure.
     */
    private static boolean nothingDue(JsonNode body) {
        if ("BFR004".equalsIgnoreCase(providerErrorCode(body))) {
            return true;
        }
        String lower = failureMessage(body).toLowerCase();
        return lower.contains("already paid") || lower.contains("no bill due");
    }

    private static String providerErrorCode(JsonNode body) {
        String code = text(unwrap(body), "providerErrorCode");
        return code == null ? text(body, "providerErrorCode") : code;
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

    private static String reference(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isTextual()) {
            String text = data.asText();
            return text == null || text.isBlank() ? null : text;
        }
        if (!data.isObject()) {
            return null;
        }
        String direct = first(data, REFERENCE_KEYS);
        if (direct != null) {
            return direct;
        }
        Iterator<JsonNode> values = data.elements();
        while (values.hasNext()) {
            JsonNode child = values.next();
            if (child != null && child.isObject()) {
                String nested = first(child, REFERENCE_KEYS);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    static String providerNotice(String message, String statusCode) {
        if (message == null) {
            return statusCode == null
                    ? "The wallet balance could not be read"
                    : "The wallet balance could not be read (" + statusCode + ")";
        }
        return statusCode == null ? message : message + " (" + statusCode + ")";
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual() && !node.asText().isBlank()) {
                return new BigDecimal(node.asText().trim().replace(",", ""));
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    private static BillingProvider.WalletBalance unreadBalance(String status, String statusCode) {
        return new BillingProvider.WalletBalance(
                false, null, status, statusCode, providerNotice(null, statusCode));
    }

    private static String dataText(JsonNode body) {
        if (body == null || !body.has("data") || body.get("data").isNull()) {
            return null;
        }
        return body.get("data").toString();
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
