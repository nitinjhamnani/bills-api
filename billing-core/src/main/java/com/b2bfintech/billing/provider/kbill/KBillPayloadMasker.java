package com.b2bfintech.billing.provider.kbill;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Masks the bearer token and customer identifiers before anything is written to a log or audit row. */
public final class KBillPayloadMasker {

    private static final Set<String> FULL_MASK = Set.of("birth_date", "dob", "dob2", "dob12", "sender_name", "flat_number", "sub_area");
    private static final Set<String> KEEP_LAST_FOUR = Set.of(
            "utility_acc_no", "utility_acc_no2", "mobile_no", "confirmation_mobile_no");

    private KBillPayloadMasker() {
    }

    public static String scrubToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) return value;
        return value.replace(token, "***");
    }

    public static String maskQuery(String rawQuery, String token) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            if (out.length() > 0) out.append('&');
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.append(scrubToken(pair, token));
                continue;
            }
            String name = decode(pair.substring(0, eq));
            String value = decode(pair.substring(eq + 1));
            out.append(name).append('=').append(maskValue(name, value));
        }
        return scrubToken(out.toString(), token);
    }

    public static String maskValue(String name, String value) {
        if (value == null || value.isBlank()) return "";
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if ("email".equals(key)) {
            int at = value.indexOf('@');
            if (at <= 0) return "***";
            return value.charAt(0) + "***" + value.substring(at);
        }
        if (FULL_MASK.contains(key) || key.startsWith("dob")) {
            return "***";
        }
        if (KEEP_LAST_FOUR.contains(key) || key.startsWith("utility_acc_no")) {
            return keepLast(value, 4);
        }
        return value;
    }

    public static String truncate(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "...(truncated)";
    }

    private static String keepLast(String value, int count) {
        if (value.length() <= count) return "***";
        return "***" + value.substring(value.length() - count);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
