package com.b2bfintech.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Assigns a request id and writes one info line per API call. Bodies stay out of this line. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request.getHeader(RequestIds.HEADER));
        MDC.put(RequestIds.MDC_KEY, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/")) {
                Object userId = request.getAttribute(RequestIds.USER_ATTRIBUTE);
                log.info("method={} path={} user={} status={} durationMs={} requestId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        userId == null ? "-" : userId,
                        response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000,
                        requestId);
            }
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private static String requestId(String incoming) {
        if (incoming != null && incoming.matches("[A-Za-z0-9-]{8,64}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
