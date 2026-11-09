package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

    @EntityGraph(attributePaths = "range")
    List<CommissionRule> findAllByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = "range")
    List<CommissionRule> findByCategoryIdAndProviderBillerId(String categoryId, String providerBillerId);

    @Override
    @EntityGraph(attributePaths = "range")
    Optional<CommissionRule> findById(UUID id);

    @EntityGraph(attributePaths = "range")
    Optional<CommissionRule> findByRange_Id(UUID rangeId);
}
