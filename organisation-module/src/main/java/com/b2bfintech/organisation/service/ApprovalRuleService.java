package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.ApprovalRule;
import com.b2bfintech.organisation.repository.ApprovalRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Configurable maker-checker thresholds per action type (implementation plan &sect;4.2). */
@Service
public class ApprovalRuleService {

    private final ApprovalRuleRepository repository;

    public ApprovalRuleService(ApprovalRuleRepository repository) {
        this.repository = repository;
    }

    public List<ApprovalRule> listAll() {
        return repository.findAll();
    }

    public ApprovalRule get(ApprovalRule.ActionType actionType) {
        return repository.findByActionType(actionType)
                .orElseGet(() -> {
                    ApprovalRule rule = new ApprovalRule();
                    rule.setActionType(actionType);
                    rule.setMinAmount(BigDecimal.ZERO);
                    rule.setRequiredApproverCount(1);
                    return repository.save(rule);
                });
    }

    public ApprovalRule update(ApprovalRule.ActionType actionType, BigDecimal minAmount, int requiredApproverCount) {
        if (requiredApproverCount < 0) {
            throw new BadRequestException("requiredApproverCount cannot be negative");
        }
        ApprovalRule rule = get(actionType);
        rule.setMinAmount(minAmount == null ? BigDecimal.ZERO : minAmount);
        rule.setRequiredApproverCount(requiredApproverCount);
        return repository.save(rule);
    }

    /** Does an action of this amount require checker approval at all? */
    public boolean requiresApproval(ApprovalRule.ActionType actionType, BigDecimal amount) {
        ApprovalRule rule = get(actionType);
        return rule.getRequiredApproverCount() > 0 && amount.compareTo(rule.getMinAmount()) >= 0;
    }
}
