package com.b2bfintech.common.security;

import com.b2bfintech.common.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header, and if present and valid:
 * (1) authenticates the request as {@link AuthenticatedUser} with a single {@code ROLE_<role>}
 * authority, and (2) sets {@link TenantContext} from the token's tenantSchema claim for the
 * duration of the request, so downstream JPA repositories resolve against the right schema.
 *
 * <p>{@link TenantContext} is always cleared in a {@code finally} block - it must never leak
 * across requests on a pooled worker thread.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                try {
                    Claims claims = jwtService.parseClaims(token);
                    String tenantSchema = claims.get(JwtService.CLAIM_TENANT_SCHEMA, String.class);
                    String role = claims.get(JwtService.CLAIM_ROLE, String.class);
                    String clientId = claims.get(JwtService.CLAIM_CLIENT_ID, String.class);

                    AuthenticatedUser principal = new AuthenticatedUser(
                            claims.getSubject(),
                            claims.get(JwtService.CLAIM_PHONE, String.class),
                            (tenantSchema == null || tenantSchema.isBlank()) ? null : tenantSchema,
                            role,
                            claims.get(JwtService.CLAIM_DISPLAY_NAME, String.class),
                            (clientId == null || clientId.isBlank()) ? null : clientId
                    );

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    if (principal.tenantSchema() != null) {
                        TenantContext.setCurrentTenant(principal.tenantSchema());
                    }
                } catch (JwtException | IllegalArgumentException ex) {
                    // Invalid/expired token: leave the request unauthenticated: Spring Security's
                    // access-decision layer will reject it for any endpoint that requires auth.
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
