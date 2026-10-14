package com.b2bfintech.app.config;

import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Stamps every log line for a request with the trace, tenant and client, so a provider call can be tied back to it. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put("traceId", traceId);
        MDC.put("tenant", TenantContext.getCurrentTenant());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user && user.clientId() != null) {
            MDC.put("clientId", user.clientId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
