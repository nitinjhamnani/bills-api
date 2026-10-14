package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.util.ClientCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /** New clients start with every category disabled. The organisation enables them per client. */
    @Transactional
    public Client create(String clientName, String contactPhone, Client.ClientType clientType) {
        Client client = new Client();
        client.setClientName(clientName);
        client.setClientCode(generateUniqueClientCode(clientName));
        client.setContactPhone(contactPhone);
        client.setClientType(clientType);
        return clientRepository.save(client);
    }

    public List<Client> listAll() {
        return clientRepository.findAll();
    }

    public Client getById(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    public Client setStatus(UUID id, Client.ClientStatus status) {
        Client client = getById(id);
        client.setStatus(status);
        return clientRepository.save(client);
    }

    /** Generates a 6-character client code (see {@link ClientCodeGenerator}), retrying on the rare
     * random collision until a free one is found. Shared with {@link #create} and the startup
     * backfill for clients onboarded before this field existed. */
    public String generateUniqueClientCode(String clientName) {
        String code;
        int attempts = 0;
        do {
            code = ClientCodeGenerator.generate(clientName);
            attempts++;
        } while (clientRepository.existsByClientCode(code) && attempts < 20);
        return code;
    }
}
