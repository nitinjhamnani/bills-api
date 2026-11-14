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

import java.time.Instant;
import java.util.UUID;

/** One change to a {@link Commission}. Kept after the commission is deleted. */
@Entity
@Table(name = "commission_log")
@Getter
@Setter
@NoArgsConstructor
public class CommissionLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "commission_id", nullable = false)
    private UUID commissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "actor_user_id", length = 6)
    private String actorUserId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum Action {
        CREATED, UPDATED, APPROVED, ACTIVATED, DEACTIVATED, DELETED
    }
}
