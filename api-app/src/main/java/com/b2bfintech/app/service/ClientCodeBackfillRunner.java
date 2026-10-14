package com.b2bfintech.app.service;

import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time-per-startup backfill: gives every client that was onboarded before the client-code
 * column existed a code, the same way a newly-onboarded client gets one ({@link
 * ClientService#generateUniqueClientCode}). Ordered to run after {@link TenantMigrationRunner} so
 * the {@code client_code} column is guaranteed to exist in every tenant schema by the time this runs.
 */
@Component
@Order(Integer.MAX_VALUE)
public class ClientCodeBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientCodeBackfillRunner.class);

    private final OrganisationRepository organisationRepository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;

    public ClientCodeBackfillRunner(
            OrganisationRepository organisationRepository, ClientRepository clientRepository, ClientService clientService) {
        this.organisationRepository = organisationRepository;
        this.clientRepository = clientRepository;
        this.clientService = clientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Organisation organisation : organisationRepository.findAll()) {
            TenantContext.setCurrentTenant(organisation.tenantSchema());
            try {
                List<Client> missingCode = clientRepository.findAll().stream()
                        .filter(c -> c.getClientCode() == null || c.getClientCode().isBlank())
                        .toList();
                for (Client client : missingCode) {
                    client.setClientCode(clientService.generateUniqueClientCode(client.getClientName()));
                    clientRepository.save(client);
                }
                if (!missingCode.isEmpty()) {
                    log.info("Backfilled client codes for {} client(s) in tenant '{}'",
                            missingCode.size(), organisation.tenantSchema());
                }
            } catch (RuntimeException ex) {
                log.error("Client code backfill failed for tenant '{}'", organisation.tenantSchema(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
