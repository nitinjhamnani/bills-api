package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.ClientCategoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientCategoryConfigRepository extends JpaRepository<ClientCategoryConfig, UUID> {
    List<ClientCategoryConfig> findByClientId(UUID clientId);

    Optional<ClientCategoryConfig> findByClientIdAndCategoryId(UUID clientId, String categoryId);
}
