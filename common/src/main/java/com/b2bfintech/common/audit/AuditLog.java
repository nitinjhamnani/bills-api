package com.b2bfintech.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One row per audited action - every money-moving action and RBAC/config change, per
 * implementation plan &sect;8.3. Tenant-schema table (every tenant has its own audit trail);
 * platform-level actions like organisation onboarding are recorded separately in
 * {@code platform.audit_log} via plain JDBC (see platform-module), since that table lives
 * outside any tenant schema.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_user_id")
    private String actorUserId; // phone number - simplest stable human-readable actor id

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
