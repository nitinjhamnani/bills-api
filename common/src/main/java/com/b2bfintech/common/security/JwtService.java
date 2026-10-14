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
import java.util.Map;

/**
 * Issues and parses the JWTs used across all three modules. A token carries the identity
 * ({@code userId}, {@code phoneNumber}), the caller's {@code role}, and - for organisation/client
 * users - the {@code tenantSchema} to route the request to (null for platform admins).
 */
@Service
public class JwtService {

    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_TENANT_SCHEMA = "tenantSchema";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_DISPLAY_NAME = "name";
    public static final String CLAIM_CLIENT_ID = "clientId";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes:60}") long accessTokenTtlMinutes) {
        // HS256 requires a key of at least 256 bits; pad/hash isn't needed as long as the
        // configured secret is long enough (see application.yml comment).
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public String generateAccessToken(String userId, String phoneNumber, String tenantSchema, String role, String displayName) {
        return generateAccessToken(userId, phoneNumber, tenantSchema, role, displayName, null);
    }

    public String generateAccessToken(String userId, String phoneNumber, String tenantSchema, String role, String displayName, String clientId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlMinutes * 60_000);
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        CLAIM_PHONE, phoneNumber,
                        CLAIM_TENANT_SCHEMA, tenantSchema == null ? "" : tenantSchema,
                        CLAIM_ROLE, role,
                        CLAIM_DISPLAY_NAME, displayName == null ? "" : displayName,
                        CLAIM_CLIENT_ID, clientId == null ? "" : clientId
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
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
