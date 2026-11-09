package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Company details plus placeholder eKYC and contract steps. One row per client. */
@Entity
@Table(name = "client_onboarding")
@Getter
@Setter
@NoArgsConstructor
public class ClientOnboarding {

    @Id
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "registered_address")
    private String registeredAddress;

    @Column(name = "signing_authority")
    private String signingAuthority;

    @Column(name = "details_submitted_at")
    private Instant detailsSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", nullable = false)
    private ContractStatus contractStatus = ContractStatus.NOT_STARTED;

    public enum KycStatus {
        NOT_STARTED, PENDING, COMPLETE
    }

    public enum ContractStatus {
        NOT_STARTED, PENDING, SIGNED
    }
}
