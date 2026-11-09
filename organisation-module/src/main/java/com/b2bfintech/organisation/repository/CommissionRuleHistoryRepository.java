package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionRuleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommissionRuleHistoryRepository extends JpaRepository<CommissionRuleHistory, UUID> {
    List<CommissionRuleHistory> findByRuleIdOrderByCreatedAtDesc(UUID ruleId);
}
