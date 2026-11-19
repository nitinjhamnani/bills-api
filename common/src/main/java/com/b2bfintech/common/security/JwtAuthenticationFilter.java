package com.b2bfintech.common.security;

import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.common.web.RequestIds;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on every request:
 * <ol>
 *   <li>Verifies JWT signature and expiration.</li>
 *   <li>Verifies the {@code sid} session is still active, unrevoked, not idle-expired, and that
 *       the user and their organisation remain active via {@link SessionValidator}.</li>
 *   <li>Sets {@code ROLE_<role>} and {@code PORTAL_<portal>} authorities on {@code SecurityContextHolder}.</li>
 * </ol>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectProvider<UserCodeLookup> userCodeLookup;
    private final ObjectProvider<SessionValidator> sessionValidatorProvider;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<UserCodeLookup> userCodeLookup,
            ObjectProvider<SessionValidator> sessionValidatorProvider) {
        this.jwtService = jwtService;
        this.userCodeLookup = userCodeLookup;
        this.sessionValidatorProvider = sessionValidatorProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                try {
                    authenticateToken(request, token);
                } catch (JwtException | IllegalArgumentException ex) {
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticateToken(HttpServletRequest request, String token) {
        Claims claims = jwtService.parseClaims(token);
        String userId = claims.getSubject();
        String sidRaw = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
        String portalRaw = claims.get(JwtService.CLAIM_PORTAL, String.class);
        String userType = claims.get(JwtService.CLAIM_USER_TYPE, String.class);
        String role = claims.get(JwtService.CLAIM_ROLE, String.class);

        if (userId == null || userId.isBlank()
                || sidRaw == null || sidRaw.isBlank()
                || portalRaw == null || portalRaw.isBlank()
                || userType == null || userType.isBlank()
                || role == null || role.isBlank()) {
            SecurityContextHolder.clearContext();
            return;
        }

        UUID sessionId = UUID.fromString(sidRaw);
        Portal portal = Portal.valueOf(portalRaw);

        SessionValidator validator = sessionValidatorProvider.getIfAvailable();
        if (validator == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        Optional<SessionValidator.ActiveSession> activeOpt =
                validator.validateAndTouch(sessionId, userId, userType);
        if (activeOpt.isEmpty()) {
            SecurityContextHolder.clearContext();
            return;
        }
        SessionValidator.ActiveSession active = activeOpt.get();
        if (active.portal() != portal || !active.role().equals(role)) {
            SecurityContextHolder.clearContext();
            return;
        }

        String tenantSchema = emptyToNull(claims.get(JwtService.CLAIM_TENANT_SCHEMA, String.class));
        String clientId = emptyToNull(active.clientId() != null ? active.clientId() : claims.get(JwtService.CLAIM_CLIENT_ID, String.class));
        String branchId = emptyToNull(claims.get(JwtService.CLAIM_BRANCH_ID, String.class));
        String userCode = emptyToNull(claims.get(JwtService.CLAIM_USER_CODE, String.class));
        if (userCode == null) {
            UserCodeLookup lookup = userCodeLookup.getIfAvailable();
            if (lookup != null) {
                userCode = lookup.findByUserId(userId).orElse(null);
            }
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                claims.get(JwtService.CLAIM_PHONE, String.class),
                tenantSchema,
                active.role(),
                claims.get(JwtService.CLAIM_DISPLAY_NAME, String.class),
                clientId,
                userCode,
                branchId,
                sessionId,
                active.portal(),
                active.userType()
        );

        List<SimpleGrantedAuthority> authorities = new ArrayList<>(3);
        authorities.add(new SimpleGrantedAuthority("PORTAL_" + active.portal().name()));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + active.role()));
        if ("SUPER_ADMIN".equals(active.role())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ORGANISATION_ADMIN"));
        }

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.copyOf(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(RequestIds.USER_ATTRIBUTE, principal.userId());

        if (principal.tenantSchema() != null) {
            TenantContext.setCurrentTenant(principal.tenantSchema());
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
