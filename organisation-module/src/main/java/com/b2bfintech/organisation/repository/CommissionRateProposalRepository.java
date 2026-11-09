package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionRateProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRateProposalRepository extends JpaRepository<CommissionRateProposal, UUID> {
    List<CommissionRateProposal> findByRuleIdAndStatus(UUID ruleId, CommissionRateProposal.Status status);

    List<CommissionRateProposal> findByRuleIdInAndStatus(Collection<UUID> ruleIds, CommissionRateProposal.Status status);

    Optional<CommissionRateProposal> findByRuleIdAndVendorClientIdAndStatus(
            UUID ruleId, UUID vendorClientId, CommissionRateProposal.Status status);

    void deleteByRuleId(UUID ruleId);

    void deleteByRuleIdAndVendorClientId(UUID ruleId, UUID vendorClientId);
}
