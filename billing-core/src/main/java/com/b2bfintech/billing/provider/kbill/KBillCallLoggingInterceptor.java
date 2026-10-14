package com.b2bfintech.billing.provider.kbill;

import com.b2bfintech.common.spi.bbps.ProviderCallRecorder;
import com.b2bfintech.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * One log line per outbound KBill call. The request factory must be buffering, otherwise reading
 * the body here would leave the mapper with an empty stream.
 */
public class KBillCallLoggingInterceptor implements ClientHttpRequestInterceptor {

    static final Logger log = LoggerFactory.getLogger("com.b2bfintech.billing.provider.kbill");

    private final KBillProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ProviderCallRecorder> recorder;

    public KBillCallLoggingInterceptor(
            KBillProperties properties, ObjectMapper objectMapper, ObjectProvider<ProviderCallRecorder> recorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.recorder = recorder;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long started = System.nanoTime();
        ClientHttpResponse response = execution.execute(request, body);
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        byte[] responseBytes = StreamUtils.copyToByteArray(response.getBody());
        String responseText = new String(responseBytes, StandardCharsets.UTF_8);
        int httpStatus = response.getStatusCode().value();
        URI uri = request.getURI();
        String operation = operationFor(uri.getPath());
        String responseCode = readCode(responseText);
        String outcome = outcome(httpStatus, responseCode, responseText);
        String clientRefId = queryValue(uri.getRawQuery(), "client_ref_id");
        String message = readMessage(responseText);

        log.info("kbill op={} method={} path={} http={} response_code={} duration_ms={} billId={} clientRefId={} outcome={} message=\"{}\"",
                operation, request.getMethod(), uri.getPath(), httpStatus, responseCode == null ? "-" : responseCode,
                durationMs, mdc("billId"), clientRefId == null ? "-" : clientRefId, outcome, message == null ? "" : message);

        if (durationMs >= properties.getSlowCallThresholdMs()) {
            log.warn("kbill slow call op={} path={} duration_ms={}", operation, uri.getPath(), durationMs);
        }
        if (!"SUCCESS".equals(outcome)) {
            String levelMessage = "kbill failure op={} http={} response_code={} outcome={} detail={}";
            if ("PERMANENT_FAILURE".equals(outcome) || "02".equals(responseCode) || "03".equals(responseCode)) {
                log.error(levelMessage, operation, httpStatus, responseCode, outcome, message);
            } else {
                log.warn(levelMessage, operation, httpStatus, responseCode, outcome, message);
            }
        }
        if (properties.isLogPayloads() && log.isDebugEnabled()) {
            String maskedQuery = KBillPayloadMasker.maskQuery(uri.getRawQuery(), properties.getApiToken());
            String bodyForLog = summarizeBody(responseText);
            log.debug("kbill payload op={} query={} body={}", operation, maskedQuery,
                    KBillPayloadMasker.scrubToken(KBillPayloadMasker.truncate(bodyForLog, properties.getMaxLoggedBodyChars()),
                            properties.getApiToken()));
        }

        if ("BILL_FETCH".equals(operation)) {
            recorder.ifAvailable(sink -> sink.record(new ProviderCallRecorder.ProviderCallRecord(
                    "KBILL", operation, TenantContext.getCurrentTenant(), mdc("billId"), clientRefId,
                    httpStatus, responseCode, durationMs,
                    KBillPayloadMasker.maskQuery(uri.getRawQuery(), properties.getApiToken()),
                    KBillPayloadMasker.scrubToken(
                            KBillPayloadMasker.truncate(summarizeBody(responseText), properties.getMaxLoggedBodyChars()),
                            properties.getApiToken()))));
        }
        return response;
    }

    private String summarizeBody(String responseText) {
        try {
            JsonNode node = objectMapper.readTree(responseText);
            JsonNode data = node.get("data");
            if (data != null && data.isArray()) {
                return "items=" + data.size();
            }
            if (data != null && data.isObject() && data.get("data") != null && data.get("data").isArray()) {
                return "items=" + data.get("data").size();
            }
        } catch (Exception ignored) {
            return KBillPayloadMasker.truncate(responseText, 200);
        }
        return responseText;
    }

    static String operationFor(String path) {
        if (path == null) return "UNKNOWN";
        if (path.endsWith("/bill-fetched") || path.contains("bill-fetch")) return "BILL_FETCH";
        if (path.contains("operator-parameters")) return "OPERATOR_PARAMETERS";
        if (path.contains("operator-list") || path.endsWith("/operator")) return "OPERATOR_LIST";
        if (path.endsWith("/location")) return "LOCATION";
        if (path.endsWith("/category")) return "CATEGORY";
        return "UNKNOWN";
    }

    private String outcome(int httpStatus, String responseCode, String responseText) {
        if (responseText != null && responseText.contains("1010")) return "TRANSIENT_FAILURE";
        if ("01".equals(responseCode) && httpStatus < 400) return "SUCCESS";
        if ("05".equals(responseCode) || httpStatus == 422) return "PERMANENT_FAILURE";
        if (httpStatus >= 200 && httpStatus < 300 && responseCode == null) return "SUCCESS";
        return "TRANSIENT_FAILURE";
    }

    private String readCode(String responseText) {
        return readField(responseText, "response_code");
    }

    private String readMessage(String responseText) {
        return readField(responseText, "message");
    }

    private String readField(String responseText, String field) {
        try {
            JsonNode node = objectMapper.readTree(responseText);
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String queryValue(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static String mdc(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }
}
