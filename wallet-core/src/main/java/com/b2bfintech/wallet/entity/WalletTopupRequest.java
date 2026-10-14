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

/** A client's wallet top-up request - amount, payment mode, a payment reference, and an optional
 * proof upload - always sitting {@code PENDING} until the organisation accepts or rejects it. */
@Entity
@Table(name = "wallet_topup_request")
@Getter
@Setter
@NoArgsConstructor
public class WalletTopupRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    @Column(name = "payment_reference", nullable = false)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum PaymentMode {
        RTGS, NEFT, UPI, CASH, OTHER
    }

    public enum Status {
        PENDING, COMPLETED, REJECTED
    }
}
