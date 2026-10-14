package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, UUID> {
    Optional<ApprovalRule> findByActionType(ApprovalRule.ActionType actionType);
}
