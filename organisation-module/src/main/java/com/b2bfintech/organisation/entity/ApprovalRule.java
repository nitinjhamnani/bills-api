package com.b2bfintech.organisation.entity;

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

/**
 * Configurable maker-checker threshold for one money-moving action type: amounts &ge;
 * {@code minAmount} require {@code requiredApproverCount} distinct CHECKER approvals
 * (implementation plan &sect;4.2 / &sect;8.1). Seeded with a threshold of 0 (always requires
 * approval) for every action type; organisation admins can raise/lower per organisation.
 */
@Entity
@Table(name = "approval_rule")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, unique = true)
    private ActionType actionType;

    @Column(name = "min_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "required_approver_count", nullable = false)
    private int requiredApproverCount = 1;

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

    public enum ActionType {
        BILL_PAYMENT,
        OFFLINE_TOPUP_CREDIT
    }
}
