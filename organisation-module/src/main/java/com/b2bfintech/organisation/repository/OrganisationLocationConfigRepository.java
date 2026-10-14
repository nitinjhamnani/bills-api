package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.OrganisationLocationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganisationLocationConfigRepository extends JpaRepository<OrganisationLocationConfig, UUID> {
    Optional<OrganisationLocationConfig> findByLocationId(String locationId);
}
