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

import java.time.Instant;
import java.util.UUID;

/** A channel partner onboarded by this organisation to sell/process bill payments for its customers. */
@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
public class Client {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    /** 6-character alphanumeric code (3 letters drawn from the client name + 3 random digits),
     * generated once at onboarding and shown everywhere the client name is - a short, stable
     * identifier that's easier to key in/read aloud than a UUID. */
    @Column(name = "client_code", unique = true, length = 6)
    private String clientCode;

    @Column(name = "contact_phone")
    private String contactPhone;

    /** CORPORATE and PARTNER are structurally identical - this is the only thing distinguishing them. */
    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false)
    private ClientType clientType = ClientType.CORPORATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

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

    public enum ClientStatus {
        ACTIVE, SUSPENDED, TERMINATED
    }

    public enum ClientType {
        CORPORATE, PARTNER
    }
}
