package com.b2bfintech.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The one wallet a corporate or a vendor holds. */
@Entity
@Table(name = "wallet", uniqueConstraints = @UniqueConstraint(columnNames = {"wallet_user_type", "wallet_user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class WalletAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_user_type", nullable = false, length = 8)
    private UserType walletUserType;

    @Column(name = "wallet_user_id", nullable = false, length = 8)
    private String walletUserId;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        lastUpdatedAt = Instant.now();
    }

    public String getClientId() {
        return walletUserId;
    }

    public WalletType getWalletType() {
        return WalletType.WALLET;
    }

    public enum UserType {
        CORP, VENDOR
    }

    /** Each owner has a single wallet. Kept so balance and ledger calls read the same as before. */
    public enum WalletType {
        WALLET
    }
}
