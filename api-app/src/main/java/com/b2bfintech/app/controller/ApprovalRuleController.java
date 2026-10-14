package com.b2bfintech.app.controller;

import com.b2bfintech.organisation.entity.ApprovalRule;
import com.b2bfintech.organisation.service.ApprovalRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Organisation admin configures maker-checker thresholds per money-moving action type. */
@RestController
@RequestMapping("/api/v1/organisation/approval-rules")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class ApprovalRuleController {

    private final ApprovalRuleService service;

    public ApprovalRuleController(ApprovalRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RuleDto> list() {
        return service.listAll().stream().map(RuleDto::from).toList();
    }

    @PutMapping("/{actionType}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public RuleDto update(@PathVariable String actionType, @RequestBody UpdateRequest request) {
        var rule = service.update(
                ApprovalRule.ActionType.valueOf(actionType.toUpperCase()), request.minAmount(), request.requiredApproverCount());
        return RuleDto.from(rule);
    }

    public record UpdateRequest(BigDecimal minAmount, int requiredApproverCount) {
    }

    public record RuleDto(String actionType, BigDecimal minAmount, int requiredApproverCount) {
        static RuleDto from(ApprovalRule r) {
            return new RuleDto(r.getActionType().name(), r.getMinAmount(), r.getRequiredApproverCount());
        }
    }
}
