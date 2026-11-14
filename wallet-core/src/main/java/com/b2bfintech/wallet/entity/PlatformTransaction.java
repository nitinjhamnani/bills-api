package com.b2bfintech.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Paytrix's side of a money movement. {@code walletId} is the corporate or vendor wallet on the other side. */
@Entity
@Table(name = "platform_transaction")
@Getter
@Setter
@NoArgsConstructor
public class PlatformTransaction {

    @Id
    @GeneratedValue
    @Column(name = "transaction_id")
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 6, updatable = false)
    private String createdBy;

    @Column(name = "transaction_category", nullable = false, length = 40)
    private String category;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum Type {
        CREDIT, DEBIT
    }
}
