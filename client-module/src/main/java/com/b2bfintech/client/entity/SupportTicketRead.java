package com.b2bfintech.client.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Per-user last-read marker for a support ticket conversation. */
@Entity
@Table(name = "support_ticket_read")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketRead {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;
}
