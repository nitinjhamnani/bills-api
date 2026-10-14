package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganisationCategoryConfigRepository extends JpaRepository<OrganisationCategoryConfig, UUID> {
    Optional<OrganisationCategoryConfig> findByCategoryId(String categoryId);
}
