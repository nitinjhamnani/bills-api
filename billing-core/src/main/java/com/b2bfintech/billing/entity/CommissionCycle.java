package com.b2bfintech.billing.entity;

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
import java.time.LocalDate;
import java.util.UUID;

/** Commission the vendor owes Paytrix for reimbursements in a period. Receiving it closes the cycle. */
@Entity
@Table(name = "commission_cycle")
@Getter
@Setter
@NoArgsConstructor
public class CommissionCycle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vendor_client_id", nullable = false)
    private UUID vendorClientId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCommission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "receipt_reference", length = 120)
    private String receiptReference;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(length = 50)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum Status {
        OPEN, CLOSED
    }
}
