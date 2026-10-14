package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.OrganisationLocationConfig;
import com.b2bfintech.organisation.repository.OrganisationLocationConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Organisation switch for locations. A missing row is disabled, so nothing is on by default. */
@Service
public class LocationEnablementService {

    private final OrganisationLocationConfigRepository repository;

    public LocationEnablementService(OrganisationLocationConfigRepository repository) {
        this.repository = repository;
    }

    public List<OrganisationLocationConfig> list() {
        return repository.findAll();
    }

    public OrganisationLocationConfig setEnabled(String locationId, boolean enabled, String actor) {
        OrganisationLocationConfig config = repository.findByLocationId(locationId)
                .orElseGet(OrganisationLocationConfig::new);
        config.setLocationId(locationId);
        config.setEnabled(enabled);
        config.setEnabledBy(actor);
        config.setEnabledAt(Instant.now());
        return repository.save(config);
    }

    public boolean isEnabled(String locationId) {
        return repository.findByLocationId(locationId).map(OrganisationLocationConfig::isEnabled).orElse(false);
    }

    public void assertEnabled(String locationId) {
        if (!isEnabled(locationId)) {
            throw new BadRequestException("Location '" + locationId + "' is not enabled for this organisation");
        }
    }
}
