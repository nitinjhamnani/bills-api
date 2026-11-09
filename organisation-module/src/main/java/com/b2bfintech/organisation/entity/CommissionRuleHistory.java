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

@Entity
@Table(name = "commission_rule_history")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRuleHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "vendor_client_id")
    private UUID vendorClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Column(name = "commission_percent", precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum EventType {
        VENDOR_ADDED,
        VENDOR_REMOVED,
        RATE_ENTERED,
        BID_SUBMITTED,
        BID_ACCEPTED,
        BID_REJECTED,
        VENDOR_PAUSED,
        VENDOR_RESUMED,
        RULE_ACTIVATED,
        RULE_DEACTIVATED
    }
}
