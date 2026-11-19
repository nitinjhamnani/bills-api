package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A vendor Paytrix onboards. Vendors settle bills and earn commission. */
@Entity
@Table(name = "vendor_details")
@Getter
@Setter
@NoArgsConstructor
public class VendorDetails {

    @Id
    @Column(name = "vendor_id", nullable = false, length = 8)
    private String vendorId;

    @Column(name = "vendor_name", nullable = false, length = 200)
    private String name;

    @Column(name = "vendor_phone", length = 15)
    private String phone;

    @Column(name = "vendor_email", length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type", nullable = false, length = 20)
    private CorpDetails.CorpType vendorType = CorpDetails.CorpType.COMPANY;

    @Column(name = "vendor_address", length = 500)
    private String address;

    @Column(name = "vendor_state", length = 100)
    private String state;

    @Column(name = "vendor_location", length = 150)
    private String location;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "signing_authority", length = 200)
    private String signingAuthority;

    @Column(name = "is_agreement_signed", nullable = false)
    private boolean agreementSigned;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 20)
    private CorpDetails.OnboardingStatus onboardingStatus = CorpDetails.OnboardingStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_status", nullable = false, length = 20)
    private CorpDetails.AgreementStatus agreementStatus = CorpDetails.AgreementStatus.NOT_STARTED;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private Client.ClientStatus accountStatus = Client.ClientStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarded_by", length = 32)
    private CorpDetails.OnboardedBy onboardedBy;

    @Column(name = "onboarded_by_id", length = 6)
    private String onboardedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
