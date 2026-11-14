package com.b2bfintech.common.web;

import org.slf4j.MDC;

/** The id stamped on each API call, shared by the access log and the error body. */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String USER_ATTRIBUTE = "b2b.userId";

    private RequestIds() {
    }

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null || id.isBlank() ? "-" : id;
    }
}
