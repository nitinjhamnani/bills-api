package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.ClientOnboarding;
import com.b2bfintech.organisation.repository.ClientOnboardingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OnboardingService {

    private final ClientOnboardingRepository onboardingRepository;
    private final ClientService clientService;

    public OnboardingService(ClientOnboardingRepository onboardingRepository, ClientService clientService) {
        this.onboardingRepository = onboardingRepository;
        this.clientService = clientService;
    }

    @Transactional
    public ClientOnboarding ensure(UUID clientId) {
        return onboardingRepository.findById(clientId).orElseGet(() -> {
            ClientOnboarding created = new ClientOnboarding();
            created.setClientId(clientId);
            created.setKycStatus(ClientOnboarding.KycStatus.PENDING);
            created.setContractStatus(ClientOnboarding.ContractStatus.PENDING);
            return onboardingRepository.save(created);
        });
    }

    public ClientOnboarding get(UUID clientId) {
        clientService.getById(clientId);
        return onboardingRepository.findById(clientId).orElseGet(() -> {
            ClientOnboarding empty = new ClientOnboarding();
            empty.setClientId(clientId);
            empty.setKycStatus(ClientOnboarding.KycStatus.NOT_STARTED);
            empty.setContractStatus(ClientOnboarding.ContractStatus.NOT_STARTED);
            return empty;
        });
    }

    public List<ClientOnboarding> list() {
        return onboardingRepository.findAll();
    }

    @Transactional
    public ClientOnboarding submitDetails(
            UUID clientId, String legalName, String registrationNumber, String registeredAddress, String signingAuthority) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        ClientOnboarding onboarding = ensure(clientId);
        boolean individual = client.getCorporateKind() == Client.CorporateKind.INDIVIDUAL;
        if (onboarding.getKycStatus() == ClientOnboarding.KycStatus.COMPLETE
                || onboarding.getContractStatus() == ClientOnboarding.ContractStatus.SIGNED) {
            throw new BadRequestException(individual
                    ? "Personal details can no longer be changed"
                    : "Company details can no longer be changed");
        }
        onboarding.setLegalName(blankToNull(legalName));
        onboarding.setRegisteredAddress(blankToNull(registeredAddress));
        if (individual) {
            onboarding.setRegistrationNumber(null);
            onboarding.setSigningAuthority(null);
        } else {
            onboarding.setRegistrationNumber(blankToNull(registrationNumber));
            onboarding.setSigningAuthority(blankToNull(signingAuthority));
        }
        onboarding.setDetailsSubmittedAt(Instant.now());
        onboarding.setKycStatus(ClientOnboarding.KycStatus.PENDING);
        if (onboarding.getContractStatus() == ClientOnboarding.ContractStatus.NOT_STARTED) {
            onboarding.setContractStatus(ClientOnboarding.ContractStatus.PENDING);
        }
        return onboardingRepository.save(onboarding);
    }

    @Transactional
    public ClientOnboarding markKycComplete(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        ClientOnboarding onboarding = ensure(clientId);
        if (onboarding.getDetailsSubmittedAt() == null) {
            throw new BadRequestException(client.getCorporateKind() == Client.CorporateKind.INDIVIDUAL
                    ? "Add personal information before eKYC"
                    : "Add company information before eKYC");
        }
        onboarding.setKycStatus(ClientOnboarding.KycStatus.COMPLETE);
        if (onboarding.getContractStatus() == ClientOnboarding.ContractStatus.NOT_STARTED) {
            onboarding.setContractStatus(ClientOnboarding.ContractStatus.PENDING);
        }
        return onboardingRepository.save(onboarding);
    }

    /** Signing the contract is the last step. A client still waiting to be activated becomes active. */
    @Transactional
    public ClientOnboarding markContractSigned(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getStatus() == Client.ClientStatus.TERMINATED) {
            throw new BadRequestException("This account is closed");
        }
        ClientOnboarding onboarding = ensure(clientId);
        if (onboarding.getKycStatus() != ClientOnboarding.KycStatus.COMPLETE) {
            throw new BadRequestException("Complete eKYC before the contract");
        }
        onboarding.setContractStatus(ClientOnboarding.ContractStatus.SIGNED);
        onboardingRepository.save(onboarding);
        if (client.getStatus() == Client.ClientStatus.PENDING) {
            clientService.setStatus(clientId, Client.ClientStatus.ACTIVE);
        }
        return onboarding;
    }

    /** Legacy clients with no onboarding row can still be reactivated. New clients always have a row. */
    public void assertCanActivate(UUID clientId) {
        ClientOnboarding onboarding = onboardingRepository.findById(clientId).orElse(null);
        if (onboarding == null) {
            return;
        }
        if (onboarding.getKycStatus() != ClientOnboarding.KycStatus.COMPLETE
                || onboarding.getContractStatus() != ClientOnboarding.ContractStatus.SIGNED) {
            throw new BadRequestException("Finish eKYC and the contract before activating this client");
        }
    }

    @Transactional
    public Client activate(UUID clientId) {
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
