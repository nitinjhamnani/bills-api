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
        return create(clientName, contactPhone, email, clientType, corporateKind, null);
    }

    @Transactional
    public Client create(String clientName, String contactPhone, String email, Client.ClientType clientType,
                          Client.CorporateKind corporateKind, String contactPersonName) {
        if (clientType == Client.ClientType.CORPORATE && corporateKind == null) {
            throw new BadRequestException("Choose individual or company");
        }
        Client client = new Client();
        client.setId(generateUniqueClientCode(clientName, clientType));
        client.setClientName(clientName);
        client.setContactPhone(contactPhone);
        client.setEmail(blankToNull(email));
        client.setSigningAuthority(blankToNull(contactPersonName));
        client.setClientType(clientType);
        client.setCorporateKind(corporateKind == null ? Client.CorporateKind.COMPANY : corporateKind);
        client.setActive(false);
        client.setAccountStatus(Client.ClientStatus.PENDING);
        client.setOnboardingStatus(blankToNull(contactPersonName) != null ? "PENDING" : "NOT_STARTED");
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

    @Transactional
    public Client updateDetails(String id, String clientName, Client.CorporateKind corporateKind) {
        return updateDetails(id, clientName, corporateKind, null, null, null);
    }

    @Transactional
    public Client updateDetails(
            String id,
            String clientName,
            Client.CorporateKind corporateKind,
            String contactPersonName,
            String contactEmail,
            String contactPhone) {
        Client client = getById(id);
        if (clientName != null && !clientName.isBlank()) {
            client.setClientName(clientName.trim());
        }
        if (corporateKind != null) {
            client.setCorporateKind(corporateKind);
        }
        if (contactPersonName != null) {
            client.setSigningAuthority(blankToNull(contactPersonName));
        }
        if (contactEmail != null) {
            client.setEmail(blankToNull(contactEmail));
        }
        if (contactPhone != null && !contactPhone.isBlank()) {
            client.setContactPhone(contactPhone.trim());
        }
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

    /**
     * Replaces the corporates linked to this sales person. A corporate left off the list is unlinked.
     * A corporate already linked to someone else moves to this person.
     */
    @Transactional
    public void replaceSalesCorporates(String salesUserId, List<String> clientIds) {
        AppUser user = appUserRepository.findById(salesUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.hasUserType(AppUser.UserType.ORG_SALES) || !user.isActive() || !user.isPaytrix()) {
            throw new BadRequestException("Choose an active sales person");
        }
        java.util.LinkedHashSet<String> wanted = new java.util.LinkedHashSet<>();
        if (clientIds != null) {
            for (String clientId : clientIds) {
                if (clientId != null && !clientId.isBlank()) {
                    wanted.add(clientId.trim());
                }
            }
        }
        for (String clientId : wanted) {
            Client client = getById(clientId);
            if (client.getClientType() != Client.ClientType.CORPORATE) {
                throw new BadRequestException("A sales person is only assigned to a corporate");
            }
        }
        for (Client current : listForSalesPerson(salesUserId)) {
            if (current.getClientType() == Client.ClientType.CORPORATE && !wanted.contains(current.getId())) {
                current.setSalesUserId(null);
                clientRepository.save(current);
            }
        }
        for (String clientId : wanted) {
            Client client = getById(clientId);
            client.setSalesUserId(salesUserId);
            clientRepository.save(client);
        }
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
