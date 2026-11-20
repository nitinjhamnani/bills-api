package com.b2bfintech.common.security;

import com.b2bfintech.common.exception.ApiErrorResponses;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless bearer-JWT HTTP security backed by live server-side {@code auth_session} validation
 * and explicit method-level {@code @PreAuthorize} rules per controller.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            ObjectProvider<UserCodeLookup> userCodeLookup,
            ObjectProvider<SessionValidator> sessionValidator,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                ApiErrorResponses.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue"))
                        .accessDeniedHandler((request, response, denied) ->
                                ApiErrorResponses.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/").permitAll()
                        .requestMatchers("/api/v1/paytrix/sales/**").access((authentication, context) -> {
                            Authentication current = authentication.get();
                            if (!isAuthenticated(current)) {
                                return new AuthorizationDecision(false);
                            }
                            return new AuthorizationDecision(
                                    hasAuthority(current, "PORTAL_PLATFORM") && hasAuthority(current, "ROLE_SALES"));
                        })
                        .requestMatchers("/api/v1/paytrix/**").access((authentication, context) -> {
                            Authentication current = authentication.get();
                            if (!isAuthenticated(current)) {
                                return new AuthorizationDecision(false);
                            }
                            if (!hasAuthority(current, "PORTAL_PLATFORM")) {
                                return new AuthorizationDecision(false);
                            }
                            if (hasAuthority(current, "ROLE_SALES")
                                    && !("GET".equals(context.getRequest().getMethod())
                                         && "/api/v1/paytrix/me".equals(context.getRequest().getRequestURI()))) {
                                return new AuthorizationDecision(false);
                            }
                            return new AuthorizationDecision(true);
                        })
                        .anyRequest().access((authentication, context) -> {
                            Authentication current = authentication.get();
                            if (!isAuthenticated(current)) {
                                return new AuthorizationDecision(false);
                            }
                            boolean sales = hasAuthority(current, "ROLE_SALES")
                                    && !"/api/v1/auth/session".equals(context.getRequest().getRequestURI())
                                    && !"/api/v1/auth/logout".equals(context.getRequest().getRequestURI())
                                    && !"/api/v1/auth/password/change".equals(context.getRequest().getRequestURI())
                                    && !"/api/v1/auth/profile".equals(context.getRequest().getRequestURI());
                            return new AuthorizationDecision(!sales);
                        })
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userCodeLookup, sessionValidator),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static boolean isAuthenticated(Authentication current) {
        return current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
    }

    private static boolean hasAuthority(Authentication authentication, String expected) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expected.equals(authority.getAuthority()));
    }

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
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
