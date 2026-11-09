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
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final AppUserRepository appUserRepository;

    public ClientService(ClientRepository clientRepository, AppUserRepository appUserRepository) {
        this.clientRepository = clientRepository;
        this.appUserRepository = appUserRepository;
    }

    /** New clients start with every category disabled. The organisation enables them per client. */
    @Transactional
    public Client create(String clientName, String contactPhone, Client.ClientType clientType,
                          Client.CorporateKind corporateKind) {
        if (clientType == Client.ClientType.VENDOR) {
            if (corporateKind != null) {
                throw new BadRequestException("A vendor is not an individual or a company");
            }
        } else if (corporateKind == null) {
            throw new BadRequestException("Choose individual or company");
        }
        Client client = new Client();
        client.setClientName(clientName);
        client.setClientCode(generateUniqueClientCode(clientName, clientType));
        client.setContactPhone(contactPhone);
        client.setClientType(clientType);
        client.setCorporateKind(corporateKind);
        client.setStatus(Client.ClientStatus.PENDING);
        return clientRepository.save(client);
    }

    public List<Client> listAll() {
        return clientRepository.findAll();
    }

    public Client getById(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    public Optional<Client> find(UUID id) {
        return clientRepository.findById(id);
    }

    public Client setStatus(UUID id, Client.ClientStatus status) {
        Client client = getById(id);
        client.setStatus(status);
        return clientRepository.save(client);
    }

    public boolean hasSalesAssignments(UUID salesUserId) {
        return clientRepository.existsBySalesUserId(salesUserId);
    }

    public List<Client> listForSalesPerson(UUID salesUserId) {
        return clientRepository.findBySalesUserIdOrderByClientNameAsc(salesUserId);
    }

    public List<Client> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return clientRepository.findAllById(ids);
    }

    /** A corporate has at most one sales person. Vendors are never linked. Passing null clears the link. */
    @Transactional
    public Client assignSalesPerson(UUID clientId, UUID salesUserId) {
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
        if (!"SALES".equals(user.getRole().getName())
                || user.getStatus() != AppUser.UserStatus.ACTIVE
                || user.getClient() != null) {
            throw new BadRequestException("Choose an active sales person");
        }
        client.setSalesUserId(salesUserId);
        return clientRepository.save(client);
    }

    /** Generates an 8-character client code (see {@link ClientCodeGenerator}), retrying on the rare
     * random collision until a free one is found. Corporates use {@code CO}, vendors use {@code VD}.
     * Shared with {@link #create} and the startup backfill for clients onboarded before this field existed. */
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
        return code;
    }
}
