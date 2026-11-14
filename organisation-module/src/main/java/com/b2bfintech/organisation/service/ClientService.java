package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.util.ClientCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final AppUserRepository appUserRepository;

    public ClientService(ClientRepository clientRepository, AppUserRepository appUserRepository) {
        this.clientRepository = clientRepository;
        this.appUserRepository = appUserRepository;
    }

    /** A new corporate or vendor stays inactive until onboarding finishes. */
    @Transactional
    public Client create(String clientName, String contactPhone, String email, Client.ClientType clientType,
                          Client.CorporateKind corporateKind) {
        if (clientType == Client.ClientType.VENDOR) {
            if (corporateKind != null) {
                throw new BadRequestException("A vendor is not an individual or a company");
            }
        } else if (corporateKind == null) {
            throw new BadRequestException("Choose individual or company");
        }
        Client client = new Client();
        client.setId(generateUniqueClientCode(clientName, clientType));
        client.setClientName(clientName);
        client.setContactPhone(contactPhone);
        client.setEmail(blankToNull(email));
        client.setClientType(clientType);
        client.setCorporateKind(clientType == Client.ClientType.VENDOR ? null : corporateKind);
        client.setActive(false);
        client.setOnboardingStatus("NOT_STARTED");
        client.setAgreementStatus("NOT_STARTED");
        client.setAgreementSigned(false);
        return clientRepository.save(client);
    }

    public List<Client> listAll() {
        return clientRepository.findAll();
    }

    public Client getById(String id) {
        return find(id).orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    public Optional<Client> find(String id) {
        return clientRepository.findById(id);
    }

    @Transactional
    public Client save(Client client) {
        return clientRepository.save(client);
    }

    public Client setStatus(String id, Client.ClientStatus status) {
        Client client = getById(id);
        client.setStatus(status);
        return clientRepository.save(client);
    }

    public boolean hasSalesAssignments(String salesUserId) {
        return clientRepository.existsBySalesUserId(salesUserId);
    }

    public List<Client> listForSalesPerson(String salesUserId) {
        return clientRepository.findBySalesUserIdOrderByClientNameAsc(salesUserId);
    }

    public List<Client> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(clientRepository::findById).flatMap(Optional::stream).toList();
    }

    /** A corporate has at most one sales person. Vendors are never linked. Passing null clears the link. */
    @Transactional
    public Client assignSalesPerson(String clientId, String salesUserId) {
        Client client = getById(clientId);
        if (client.getClientType() != Client.ClientType.CORPORATE) {
            throw new BadRequestException("A sales person is only assigned to a corporate");
        }
        if (salesUserId == null) {
            client.setSalesUserId(null);
            return clientRepository.save(client);
        }
        AppUser user = appUserRepository.findById(salesUserId)
                .orElseThrow(() -> new BadRequestException("Choose an active sales person"));
        if (!user.hasUserType(AppUser.UserType.ORG_SALES) || !user.isActive() || !user.isPaytrix()) {
            throw new BadRequestException("Choose an active sales person");
        }
        client.setSalesUserId(salesUserId);
        return clientRepository.save(client);
    }

    /** Corporates use {@code CO}. Vendors use {@code VD}. Both ids are 8 characters. */
    public String generateUniqueClientCode(String clientName, Client.ClientType clientType) {
        String prefix = clientType == Client.ClientType.VENDOR
                ? ClientCodeGenerator.VENDOR_PREFIX
                : ClientCodeGenerator.CORPORATE_PREFIX;
        String code;
        int attempts = 0;
        do {
            code = ClientCodeGenerator.generate(clientName, prefix);
            attempts++;
        } while (clientRepository.existsByClientCode(code) && attempts < 20);
        if (clientRepository.existsByClientCode(code)) {
            throw new BadRequestException("Could not assign a client id");
        }
        return code;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
