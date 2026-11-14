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

/** A corporate Paytrix onboards. Corporates create and pay bills. */
@Entity
@Table(name = "corp_details")
@Getter
@Setter
@NoArgsConstructor
public class CorpDetails {

    @Id
    @Column(name = "corp_id", nullable = false, length = 8)
    private String corpId;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String name;

    @Column(name = "corp_phone", length = 15)
    private String phone;

    @Column(name = "corp_email", length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "corp_type", nullable = false, length = 20)
    private CorpType corpType;

    @Column(name = "corp_address", length = 500)
    private String address;

    @Column(name = "corp_state", length = 100)
    private String state;

    @Column(name = "corp_location", length = 150)
    private String location;

    @Column(name = "is_agreement_signed", nullable = false)
    private boolean agreementSigned;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 20)
    private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_status", nullable = false, length = 20)
    private AgreementStatus agreementStatus = AgreementStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarded_by", length = 32)
    private OnboardedBy onboardedBy;

    @Column(name = "onboarded_by_id", length = 6)
    private String onboardedById;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public enum CorpType {
        COMPANY, INDIVIDUAL
    }

    public enum OnboardingStatus {
        NOT_STARTED, PENDING, COMPLETE
    }

    public enum AgreementStatus {
        NOT_STARTED, PENDING, SIGNED
    }

    public enum OnboardedBy {
        ORG_ADMIN, ORG_SALES
    }
}
