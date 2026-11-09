package com.b2bfintech.app.service;

import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives every client that was onboarded before the client-code column existed a code, the same
 * way a newly onboarded client gets one.
 */
@Component
public class ClientCodeBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientCodeBackfillRunner.class);

    private final ClientRepository clientRepository;
    private final ClientService clientService;

    public ClientCodeBackfillRunner(ClientRepository clientRepository, ClientService clientService) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Client> missingCode = clientRepository.findAll().stream()
                .filter(c -> c.getClientCode() == null || c.getClientCode().isBlank())
                .toList();
        for (Client client : missingCode) {
            client.setClientCode(clientService.generateUniqueClientCode(client.getClientName(), client.getClientType()));
            clientRepository.save(client);
        }
        if (!missingCode.isEmpty()) {
            log.info("Backfilled client codes for {} client(s)", missingCode.size());
        }
    }
}
