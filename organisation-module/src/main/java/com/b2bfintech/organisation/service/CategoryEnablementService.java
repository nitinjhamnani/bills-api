package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.ClientCategoryConfig;
import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import com.b2bfintech.organisation.repository.ClientCategoryConfigRepository;
import com.b2bfintech.organisation.repository.OrganisationCategoryConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Two-level category switch. The organisation opts in first. Once it has, every client has that
 * category on until someone turns it off for that client. A missing client row means enabled.
 */
@Service
public class CategoryEnablementService {

    private final OrganisationCategoryConfigRepository organisationRepository;
    private final ClientCategoryConfigRepository clientRepository;

    public CategoryEnablementService(
            OrganisationCategoryConfigRepository organisationRepository,
            ClientCategoryConfigRepository clientRepository) {
        this.organisationRepository = organisationRepository;
        this.clientRepository = clientRepository;
    }

    public List<OrganisationCategoryConfig> listOrganisation() {
        return organisationRepository.findAll();
    }

    public OrganisationCategoryConfig setOrganisationEnabled(String categoryId, boolean enabled, String actor) {
        OrganisationCategoryConfig config = organisationRepository.findByCategoryId(categoryId)
                .orElseGet(OrganisationCategoryConfig::new);
        config.setCategoryId(categoryId);
        config.setEnabled(enabled);
        config.setEnabledBy(actor);
        config.setEnabledAt(Instant.now());
        return organisationRepository.save(config);
    }

    public boolean isOrganisationEnabled(String categoryId) {
        return organisationRepository.findByCategoryId(categoryId).map(OrganisationCategoryConfig::isEnabled).orElse(false);
    }

    public List<ClientCategoryConfig> listClientRows(UUID clientId) {
        return clientRepository.findByClientId(clientId);
    }

    public ClientCategoryConfig setClientEnabled(Client client, String categoryId, boolean enabled, String actor) {
        if (!isOrganisationEnabled(categoryId)) {
            throw new BadRequestException("Category '" + categoryId + "' is not enabled for this organisation");
        }
        ClientCategoryConfig config = clientRepository.findByClientIdAndCategoryId(client.getId(), categoryId)
                .orElseGet(ClientCategoryConfig::new);
        config.setClient(client);
        config.setCategoryId(categoryId);
        config.setEnabled(enabled);
        config.setEnabledBy(actor);
        config.setEnabledAt(Instant.now());
        return clientRepository.save(config);
    }

    public void assertEnabled(UUID clientId, String categoryId) {
        if (!isOrganisationEnabled(categoryId)) {
            throw new BadRequestException("Category '" + categoryId + "' is not enabled for this organisation");
        }
        clientRepository.findByClientIdAndCategoryId(clientId, categoryId).ifPresent(clientConfig -> {
            if (!clientConfig.isEnabled()) {
                throw new BadRequestException("Category '" + categoryId + "' is disabled for this client");
            }
        });
    }
}
