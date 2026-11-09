package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One vendor invited onto a rule. A null percent means they have no live rate yet. */
@Entity
@Table(name = "commission_rule_vendor")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRuleVendor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "vendor_client_id", nullable = false)
    private UUID vendorClientId;

    @Column(name = "commission_percent", precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    /** A paused vendor keeps their rate but is left out of assignment. */
    @Column(nullable = false)
    private boolean active = true;

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
}
