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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A corporate admin's wallet top-up request. It stays PENDING until Paytrix approves or rejects it. */
@Entity
@Table(name = "wallet_transaction_request")
@Getter
@Setter
@NoArgsConstructor
public class WalletTransactionRequest {

    @Id
    @GeneratedValue
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "corp_id", nullable = false, length = 8)
    private String corpId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private Mode mode;

    @Column(name = "utr", nullable = false, length = 150)
    private String utr;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "created_by", length = 150, updatable = false)
    private String createdBy;

    @Column(name = "created_by_id", length = 6, updatable = false)
    private String createdById;

    @Column(name = "proof", nullable = false, length = 500)
    private String proof;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_by_id", length = 6)
    private String approvedById;

    @Column(name = "approved_on")
    private Instant approvedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdDate = now;
        lastUpdated = now;
    }

    @PreUpdate
    void onUpdate() {
        lastUpdated = Instant.now();
    }

    public enum Mode {
        RTGS, NEFT, UPI, CASH, OTHER
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }
}
