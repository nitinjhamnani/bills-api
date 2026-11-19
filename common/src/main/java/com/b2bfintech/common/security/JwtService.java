package com.b2bfintech.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and parses short-lived signed JWT access tokens across Platform, Corporate, and Vendor portals.
 */
@Service
public class JwtService {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_PORTAL = "portal";
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_USER_CODE = "userCode";
    public static final String CLAIM_TENANT_SCHEMA = "tenantSchema";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_DISPLAY_NAME = "name";
    public static final String CLAIM_CLIENT_ID = "clientId";
    public static final String CLAIM_BRANCH_ID = "branchId";

    private static final String KNOWN_DEV_PLACEHOLDER =
            "dev-only-b2bfintech-jwt-signing-secret-change-me-before-any-shared-deploy";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;

    public record TokenSubject(
            UUID sessionId,
            String userId,
            String phoneNumber,
            String userCode,
            Portal portal,
            String userType,
            String role,
            String displayName,
            String clientId,
            String branchId,
            String tenantSchema
    ) {
    }

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured and at least 32 bytes long");
        }
        byte[] keyBytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits) for HS256");
        }
        if (KNOWN_DEV_PLACEHOLDER.equals(secret.trim())) {
            throw new IllegalStateException("JWT_SECRET must not use the legacy repository placeholder");
        }
        if (accessTokenTtlMinutes <= 0) {
            throw new IllegalStateException("app.jwt.access-token-ttl-minutes must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public String generateAccessToken(TokenSubject subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlMinutes * 60_000L);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_SESSION_ID, subject.sessionId().toString());
        claims.put(CLAIM_PORTAL, subject.portal().name());
        claims.put(CLAIM_USER_TYPE, subject.userType());
        claims.put(CLAIM_PHONE, subject.phoneNumber());
        claims.put(CLAIM_USER_CODE, subject.userCode() == null ? "" : subject.userCode());
        claims.put(CLAIM_TENANT_SCHEMA, subject.tenantSchema() == null ? "" : subject.tenantSchema());
        claims.put(CLAIM_ROLE, subject.role());
        claims.put(CLAIM_DISPLAY_NAME, subject.displayName() == null ? "" : subject.displayName());
        claims.put(CLAIM_CLIENT_ID, subject.clientId() == null ? "" : subject.clientId());
        claims.put(CLAIM_BRANCH_ID, subject.branchId() == null ? "" : subject.branchId());

        return Jwts.builder()
                .subject(subject.userId())
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60L;
    }

    /**
     * @throws JwtException if the token is malformed, expired, or has an invalid signature.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
