package com.b2bfintech.app.controller;

import com.b2bfintech.organisation.entity.FeeConfig;
import com.b2bfintech.organisation.service.FeeConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Organisation-to-client convenience fee configuration, optionally per bill service (implementation plan &sect;8.2). */
@RestController
@RequestMapping("/api/v1/organisation/fee-config")
@PreAuthorize("hasRole('ORGANISATION_ADMIN')")
public class FeeConfigController {

    private final FeeConfigService feeConfigService;

    public FeeConfigController(FeeConfigService feeConfigService) {
        this.feeConfigService = feeConfigService;
    }

    @GetMapping
    public List<FeeDto> list() {
        return feeConfigService.listAll().stream().map(FeeDto::from).toList();
    }

    @PutMapping
    public FeeDto upsert(@RequestBody UpsertRequest request) {
        var config = feeConfigService.upsert(request.billServiceCode(), request.feeType(), request.value(), request.enabled());
        return FeeDto.from(config);
    }

    public record UpsertRequest(String billServiceCode, FeeConfig.FeeType feeType, BigDecimal value, boolean enabled) {
    }

    public record FeeDto(String billServiceCode, String feeType, BigDecimal value, boolean enabled) {
        static FeeDto from(FeeConfig c) {
            return new FeeDto(c.getBillServiceCode(), c.getFeeType().name(), c.getValue(), c.isEnabled());
        }
    }
}
