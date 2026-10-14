package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, UUID> {
    Optional<FeeConfig> findByBillServiceCodeAndEnabledTrue(String billServiceCode);
    Optional<FeeConfig> findByBillServiceCodeIsNullAndEnabledTrue();
    List<FeeConfig> findAll();
}
