package com.b2bfintech.common.exception;

import com.b2bfintech.common.web.RequestIds;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** The `{timestamp, status, errorCode, message}` body. Stack traces and SQL stay on the server. */
public final class ApiErrorResponses {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorResponses.class);

    private ApiErrorResponses() {
    }

    public static Map<String, Object> body(HttpStatus status, String errorCode, String message) {
        log.info("api_error code={} requestId={} status={}", errorCode, RequestIds.current(), status.value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("errorCode", errorCode);
        body.put("message", message);
        return body;
    }

    public static void write(HttpServletResponse response, HttpStatus status, String errorCode, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Map<String, Object> body = body(status, errorCode, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + body.get("timestamp")
                + "\",\"status\":" + body.get("status")
                + ",\"errorCode\":\"" + escape((String) body.get("errorCode"))
                + "\",\"message\":\"" + escape((String) body.get("message")) + "\"}");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
