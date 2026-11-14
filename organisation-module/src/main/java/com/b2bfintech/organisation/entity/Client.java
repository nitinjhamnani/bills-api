package com.b2bfintech.organisation.entity;

/**
 * The corporate or vendor the rest of the application already talks to.
 * The row itself lives in {@code corp_details} or {@code vendor_details}.
 */
public class Client {

    private String id;
    private String clientName;
    private String contactPhone;
    private String email;
    private String address;
    private String state;
    private String location;
    private ClientType clientType;
    private CorporateKind corporateKind;
    private boolean active;
    private String onboardingStatus;
    private String agreementStatus;
    private boolean agreementSigned;
    private String onboardedBy;
    private String onboardedById;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** The id is the code. A corporate id starts with CO. */
    public String getClientCode() {
        return id;
    }

    public void setClientCode(String clientCode) {
        if (id == null || id.isBlank()) {
            id = clientCode;
        }
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public CorporateKind getCorporateKind() {
        return corporateKind;
    }

    public void setCorporateKind(CorporateKind corporateKind) {
        this.corporateKind = corporateKind;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public String getAgreementStatus() {
        return agreementStatus;
    }

    public void setAgreementStatus(String agreementStatus) {
        this.agreementStatus = agreementStatus;
    }

    public boolean isAgreementSigned() {
        return agreementSigned;
    }

    public void setAgreementSigned(boolean agreementSigned) {
        this.agreementSigned = agreementSigned;
    }

    public String getOnboardedBy() {
        return onboardedBy;
    }

    public void setOnboardedBy(String onboardedBy) {
        this.onboardedBy = onboardedBy;
    }

    public String getOnboardedById() {
        return onboardedById;
    }

    public void setOnboardedById(String onboardedById) {
        this.onboardedById = onboardedById;
    }

    /** Active companies stay active. One that has not finished eKYC and the contract is still pending. */
    public ClientStatus getStatus() {
        if (active) {
            return ClientStatus.ACTIVE;
        }
        if (clientType == ClientType.CORPORATE
                && (!"COMPLETE".equals(onboardingStatus) || !"SIGNED".equals(agreementStatus))) {
            return ClientStatus.PENDING;
        }
        if (clientType == ClientType.VENDOR && !"SIGNED".equals(agreementStatus)) {
            return ClientStatus.PENDING;
        }
        return ClientStatus.SUSPENDED;
    }

    public void setStatus(ClientStatus status) {
        this.active = status == ClientStatus.ACTIVE;
    }

    /** The sales person, when a corporate was brought in by sales. */
    public java.time.Instant getCreatedAt() {
        return null;
    }

    public String getSalesUserId() {
        return "ORG_SALES".equals(onboardedBy) ? onboardedById : null;
    }

    public void setSalesUserId(String salesUserId) {
        if (salesUserId == null || salesUserId.isBlank()) {
            if ("ORG_SALES".equals(onboardedBy)) {
                onboardedBy = null;
                onboardedById = null;
            }
            return;
        }
        onboardedBy = "ORG_SALES";
        onboardedById = salesUserId;
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
