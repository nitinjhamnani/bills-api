package com.b2bfintech.billing.provider.kbill;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Turns a KBill HTTP body into the payload node, or a classified failure.
 *
 * <p>The live API is inconsistent: {@code /category} wraps the list in {@code data.data}, every
 * other call returns a flat {@code data} array, HTTP status does not match the documented
 * {@code response_code}, and some validation errors omit {@code response_code} entirely. Success
 * is therefore decided from {@code status}/{@code response_code} when they are present, and from
 * the HTTP status only when the body has neither.
 */
public final class KBillEnvelopeReader {

    private KBillEnvelopeReader() {
    }

    public static JsonNode payload(JsonNode body, int httpStatus) {
        if (body == null || body.isNull() || body.isMissingNode() || !body.isObject()) {
            throw failure(httpStatus, null, "Bill provider returned a non-JSON response", false);
        }
        if (isCloudflareBlock(body)) {
            throw failure(httpStatus, null, "Bill provider blocked the request (Cloudflare 1010). Check the User-Agent.", false);
        }
        boolean hasStatus = body.has("status");
        boolean hasCode = body.hasNonNull("response_code");
        boolean status = body.path("status").asBoolean(false);
        String code = text(body, "response_code");
        String message = firstText(body, "message");
        if (message == null) {
            message = laravelMessage(body);
        }
        String errors = flattenErrors(body.get("errors"));
        String detail = join(message, errors);

        if (!hasStatus && !hasCode) {
            boolean permanent = httpStatus == 422;
            throw failure(httpStatus, null, detail == null ? "Bill provider returned HTTP " + httpStatus : detail, permanent);
        }
        if (status && "01".equals(code)) {
            return unwrap(body.get("data"));
        }
        if (status && code == null && httpStatus < 400) {
            return unwrap(body.get("data"));
        }
        boolean permanent = "05".equals(code) || httpStatus == 422;
        throw failure(httpStatus, code, detail == null ? "Bill provider call failed" : detail, permanent);
    }

    static JsonNode unwrap(JsonNode data) {
        if (data != null && data.isObject() && data.get("data") != null && data.get("data").isArray()) {
            return data.get("data");
        }
        return data == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : data;
    }

    private static boolean isCloudflareBlock(JsonNode body) {
        String title = text(body, "title");
        return title != null && title.contains("1010");
    }

    private static String laravelMessage(JsonNode body) {
        String message = text(body, "message");
        if (message != null && (message.contains("could not be found") || message.contains("not supported"))) {
            return message;
        }
        return message;
    }

    static String flattenErrors(JsonNode errors) {
        if (errors == null || !errors.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = errors.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value.isArray()) {
                for (JsonNode item : value) {
                    parts.add(field.getKey() + ": " + item.asText());
                }
            } else {
                parts.add(field.getKey() + ": " + value.asText());
            }
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static String join(String message, String errors) {
        if (message == null) return errors;
        if (errors == null) return message;
        return message + " (" + errors + ")";
    }

    private static KBillCallException failure(int httpStatus, String code, String message, boolean permanent) {
        String prefix = code == null ? "" : "[" + code + "] ";
        return new KBillCallException(prefix + message, code, permanent, httpStatus);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstText(JsonNode node, String field) {
        return text(node, field);
    }
}
