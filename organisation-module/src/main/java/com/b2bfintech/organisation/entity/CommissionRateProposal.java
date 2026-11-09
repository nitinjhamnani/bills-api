package com.b2bfintech.organisation.entity;

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

/** A vendor's requested commission for one rule. It applies only after Paytrix accepts it. */
@Entity
@Table(name = "commission_rate_proposal")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRateProposal {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "vendor_client_id", nullable = false)
    private UUID vendorClientId;

    @Column(name = "proposed_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal proposedPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }
}
