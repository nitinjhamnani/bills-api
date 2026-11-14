package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.ClientOnboarding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final ClientService clientService;

    public OnboardingService(ClientService clientService) {
        this.clientService = clientService;
    }

    public ClientOnboarding ensure(String clientId) {
        return get(clientId);
    }

    public ClientOnboarding get(String clientId) {
        return ClientOnboarding.from(clientService.getById(clientId));
    }

    @Transactional
    public ClientOnboarding submitDetails(
            String clientId, String legalName, String registrationNumber, String registeredAddress, String signingAuthority) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        ClientOnboarding current = ClientOnboarding.from(client);
        boolean individual = client.getCorporateKind() == Client.CorporateKind.INDIVIDUAL;
        if (current.getKycStatus() == ClientOnboarding.KycStatus.COMPLETE
                || current.getContractStatus() == ClientOnboarding.ContractStatus.SIGNED) {
            throw new BadRequestException(individual
                    ? "Personal details can no longer be changed"
                    : "Company details can no longer be changed");
        }
        String name = blankToNull(legalName);
        if (name != null) {
            client.setClientName(name);
        }
        client.setAddress(blankToNull(registeredAddress));
        if (client.getClientType() == Client.ClientType.CORPORATE && "NOT_STARTED".equals(client.getOnboardingStatus())) {
            client.setOnboardingStatus("PENDING");
        }
        if ("NOT_STARTED".equals(client.getAgreementStatus())) {
            client.setAgreementStatus("PENDING");
        }
        return ClientOnboarding.from(clientService.save(client));
    }

    @Transactional
    public ClientOnboarding markKycComplete(String clientId) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        boolean detailsMissing = client.getClientType() == Client.ClientType.VENDOR
                ? "NOT_STARTED".equals(client.getAgreementStatus())
                : "NOT_STARTED".equals(client.getOnboardingStatus());
        if (detailsMissing) {
            throw new BadRequestException(client.getCorporateKind() == Client.CorporateKind.INDIVIDUAL
                    ? "Add personal information before eKYC"
                    : "Add company information before eKYC");
        }
        if (client.getClientType() == Client.ClientType.CORPORATE) {
            client.setOnboardingStatus("COMPLETE");
            if ("NOT_STARTED".equals(client.getAgreementStatus())) {
                client.setAgreementStatus("PENDING");
            }
            client = clientService.save(client);
        }
        return ClientOnboarding.from(client);
    }

    /** Signing the contract is the last step. A client still waiting to be activated becomes active. */
    @Transactional
    public ClientOnboarding markContractSigned(String clientId) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        ClientOnboarding current = ClientOnboarding.from(client);
        if (current.getKycStatus() != ClientOnboarding.KycStatus.COMPLETE) {
            throw new BadRequestException("Complete eKYC before the contract");
        }
        boolean wasPending = client.getStatus() == Client.ClientStatus.PENDING;
        client.setAgreementStatus("SIGNED");
        client.setAgreementSigned(true);
        client = clientService.save(client);
        if (wasPending) {
            clientService.setStatus(clientId, Client.ClientStatus.ACTIVE);
            client = clientService.getById(clientId);
        }
        return ClientOnboarding.from(client);
    }

    public void assertCanActivate(String clientId) {
        ClientOnboarding onboarding = get(clientId);
        if (onboarding.getKycStatus() != ClientOnboarding.KycStatus.COMPLETE
                || onboarding.getContractStatus() != ClientOnboarding.ContractStatus.SIGNED) {
            throw new BadRequestException("Finish eKYC and the contract before activating this client");
        }
    }

    @Transactional
    public Client activate(String clientId) {
        assertCanActivate(clientId);
        return clientService.setStatus(clientId, Client.ClientStatus.ACTIVE);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
