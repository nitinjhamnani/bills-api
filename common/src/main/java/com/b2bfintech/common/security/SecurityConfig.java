package com.b2bfintech.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless, JWT-only security: no sessions, no CSRF (no cookies involved), method-level
 * {@code @PreAuthorize} for RBAC. Auth (password login) and actuator health are public;
 * everything else requires a valid JWT.
 *
 * <p>CORS is explicit here rather than left to Spring's defaults: the Next.js frontend runs on a
 * different origin (localhost:3001 locally) and calls the API with a JSON body, which browsers
 * always preflight with an OPTIONS request - without a registered {@link CorsConfigurationSource},
 * Spring Security rejects that preflight with a 403 before it ever reaches a controller, which
 * surfaces in the browser as an opaque "failed to fetch"/CORS error, not a 403 anyone sees logged
 * server-side.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtService jwtService, ObjectProvider<UserCodeLookup> userCodeLookup,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/").permitAll()
                        .requestMatchers("/api/v1/paytrix/sales/**").hasRole("SALES")
                        .requestMatchers(HttpMethod.GET, "/api/v1/paytrix/me").authenticated()
                        .anyRequest().access((authentication, context) -> {
                            Authentication current = authentication.get();
                            if (current == null || !current.isAuthenticated()
                                    || current instanceof AnonymousAuthenticationToken) {
                                return new AuthorizationDecision(false);
                            }
                            boolean sales = hasRole(current, "ROLE_SALES");
                            if (sales) {
                                return new AuthorizationDecision(false);
                            }
                            if (hasRole(current, "ROLE_BRANCH_USER")) {
                                return new AuthorizationDecision(branchUserAllowed(context));
                            }
                            return new AuthorizationDecision(true);
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userCodeLookup), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    /** Branch users register bills and watch cycles. They cannot pay, open a wallet, or use Paytrix. */
    private static boolean branchUserAllowed(RequestAuthorizationContext context) {
        String path = context.getRequest().getRequestURI();
        String method = context.getRequest().getMethod();
        if (path.startsWith("/api/v1/paytrix/") || path.contains("/wallet") || path.contains("/reports") || path.contains("/users")) {
            return false;
        }
        if (path.contains("/my-payments/pay") || ("POST".equals(method) && path.contains("/my-payments"))) {
            return false;
        }
        return path.equals("/api/v1/client/account")
                || path.startsWith("/api/v1/client/bbps")
                || path.contains("/managed-bills")
                || path.contains("/my-payments")
                || path.contains("/support-tickets")
                || path.contains("/branch-home")
                || path.contains("/branches");
    }

    /** Hashes stored passwords used at login. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
