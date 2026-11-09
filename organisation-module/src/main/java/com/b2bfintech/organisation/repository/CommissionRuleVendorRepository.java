package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionRuleVendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRuleVendorRepository extends JpaRepository<CommissionRuleVendor, UUID> {
    List<CommissionRuleVendor> findByRuleId(UUID ruleId);

    List<CommissionRuleVendor> findByRuleIdIn(Collection<UUID> ruleIds);

    List<CommissionRuleVendor> findByVendorClientId(UUID vendorClientId);

    Optional<CommissionRuleVendor> findByRuleIdAndVendorClientId(UUID ruleId, UUID vendorClientId);
}
