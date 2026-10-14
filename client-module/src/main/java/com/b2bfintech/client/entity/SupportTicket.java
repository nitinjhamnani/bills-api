package com.b2bfintech.client.entity;

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

/** A support ticket a client raises for the organisation to see and resolve. */
@Entity
@Table(name = "support_ticket")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "raised_by")
    private String raisedBy;

    @Column(name = "picked_up_by")
    private String pickedUpBy;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

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
        OPEN, IN_PROGRESS, RESOLVED
    }
}
