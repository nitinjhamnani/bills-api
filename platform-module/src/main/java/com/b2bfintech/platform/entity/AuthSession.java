package com.b2bfintech.platform.entity;

import com.b2bfintech.common.security.Portal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_session")
@Getter
@Setter
@NoArgsConstructor
public class AuthSession {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, length = 6)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "portal", nullable = false, length = 16)
    private Portal portal;

    @Column(name = "user_type", nullable = false, length = 32)
    private String userType;

    @Column(name = "role_name", nullable = false, length = 32)
    private String roleName;

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "refresh_hash", nullable = false, length = 128)
    private String refreshHash;

    @Column(name = "prev_refresh_hash", length = 128)
    private String prevRefreshHash;

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @Column(name = "last_seen_on", nullable = false)
    private Instant lastSeenOn;

    @Column(name = "expires_on", nullable = false, updatable = false)
    private Instant expiresOn;

    @Column(name = "revoked_on")
    private Instant revokedOn;

    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public boolean isRevoked() {
        return revokedOn != null;
    }
}
