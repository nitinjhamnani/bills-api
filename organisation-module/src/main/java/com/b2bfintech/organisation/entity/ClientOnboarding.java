package com.b2bfintech.organisation.entity;


/** Onboarding as the screens already read it. The stored columns live on the corporate or vendor. */
public class ClientOnboarding {

    private String clientId;
    private String legalName;
    private String registrationNumber;
    private String registeredAddress;
    private String signingAuthority;
    private boolean detailsSubmitted;
    private KycStatus kycStatus = KycStatus.NOT_STARTED;
    private ContractStatus contractStatus = ContractStatus.NOT_STARTED;

    public static ClientOnboarding from(Client client) {
        ClientOnboarding view = new ClientOnboarding();
        view.clientId = client.getId();
        view.legalName = client.getClientName();
        view.registrationNumber = client.getRegistrationNumber();
        view.registeredAddress = client.getAddress();
        view.signingAuthority = client.getSigningAuthority();
        view.detailsSubmitted = !"NOT_STARTED".equals(client.getOnboardingStatus())
                || !"NOT_STARTED".equals(client.getAgreementStatus());
        view.kycStatus = KycStatus.valueOf(
                client.getOnboardingStatus() == null ? "NOT_STARTED" : client.getOnboardingStatus());
        view.contractStatus = ContractStatus.valueOf(
                client.getAgreementStatus() == null ? "NOT_STARTED" : client.getAgreementStatus());
        return view;
    }

    public String getClientId() {
        return clientId;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getRegisteredAddress() {
        return registeredAddress;
    }

    public String getSigningAuthority() {
        return signingAuthority;
    }

    /** Company or personal information was saved. Saving moves the stored status off NOT_STARTED. */
    public boolean isDetailsSubmitted() {
        return detailsSubmitted;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public ContractStatus getContractStatus() {
        return contractStatus;
    }

    public enum KycStatus {
        NOT_STARTED, PENDING, COMPLETE
    }

    public enum ContractStatus {
        NOT_STARTED, PENDING, SIGNED
    }
}
