package com.b2bfintech.client.entity;

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

/** One message in a support ticket thread between the client and the organisation. */
@Entity
@Table(name = "support_ticket_message")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_side", nullable = false)
    private AuthorSide authorSide;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum AuthorSide {
        CLIENT, ORGANISATION, SYSTEM
    }
}
