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

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only, timestamped activity entry for a {@link Bill} - creation, every fetch outcome,
 * the client's payment, the organisation's pick-up/paid/failed actions. {@code billPaymentId} is
 * null for bill-level-only events (creation, a fetch that never became payable).
 */
@Entity
@Table(name = "bill_payment_log")
@Getter
@Setter
@NoArgsConstructor
public class BillPaymentLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_payment_id")
    private UUID billPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column
    private String description;

    /** Phone number of the acting user, or null for a scheduled/system event. */
    @Column
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum EventType {
        CREATED, FETCH_SUCCESS, FETCH_FAILED_TRANSIENT, FETCH_FAILED_PERMANENT,
        CLIENT_PAID, ORG_PICKED_UP, ORG_PAID, ORG_MARK_FAILED
    }
}
