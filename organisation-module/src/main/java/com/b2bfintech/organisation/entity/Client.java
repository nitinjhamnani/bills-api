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

/** A company Paytrix onboards. Corporates create and pay bills. Vendors are the other client type. */
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

    /** 8-character code: {@code CO} or {@code VD}, then 3 letters from the client name and 3 random digits,
     * generated once at onboarding and shown everywhere the client name is. */
    @Column(name = "client_code", unique = true, length = 8)
    private String clientCode;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false)
    private ClientType clientType = ClientType.CORPORATE;

    /** Set only for a corporate. Chooses the onboarding form. Vendors stay null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "corporate_kind", length = 20)
    private CorporateKind corporateKind;

    /** New clients stay PENDING until Paytrix activates them. Existing rows keep whatever status they already have. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The Paytrix sales person who brought this corporate in. One corporate has at most one. */
    @Column(name = "sales_user_id")
    private UUID salesUserId;

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
        PENDING, ACTIVE, SUSPENDED, TERMINATED
    }

    public enum ClientType {
        CORPORATE, VENDOR
    }

    public enum CorporateKind {
        COMPANY, INDIVIDUAL
    }
}
