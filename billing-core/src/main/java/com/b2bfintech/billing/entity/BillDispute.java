package com.b2bfintech.billing.entity;

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

import java.time.Instant;
import java.util.UUID;

/** A customer-disputed bill payment a client raises on the customer's behalf (implementation plan &sect;8). */
@Entity
@Table(name = "bill_dispute")
@Getter
@Setter
@NoArgsConstructor
public class BillDispute {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_payment_id", nullable = false)
    private UUID billPaymentId;

    @Column(name = "raised_by")
    private String raisedBy;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RAISED;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private String resolvedBy;

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

    public enum Status {
        RAISED, UNDER_REVIEW, RESOLVED_VALID, RESOLVED_INVALID
    }
}
