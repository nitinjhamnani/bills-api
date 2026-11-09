package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.entity.CommissionAmountRange;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.CommissionRangeService;
import com.b2bfintech.organisation.service.CommissionRuleService;
import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.CommissionBillGuard;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paytrix/commissions")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class CommissionController {

    private final CommissionRangeService rangeService;
    private final CommissionRuleService ruleService;
    private final BbpsCategoryRepository categoryRepository;
    private final CategoryEnablementService categoryEnablementService;
    private final BbpsLookupService lookupService;
    private final CommissionBillGuard billGuard;

    public CommissionController(
            CommissionRangeService rangeService, CommissionRuleService ruleService,
            BbpsCategoryRepository categoryRepository,
            CategoryEnablementService categoryEnablementService,
            BbpsLookupService lookupService, CommissionBillGuard billGuard) {
        this.rangeService = rangeService;
        this.ruleService = ruleService;
        this.categoryRepository = categoryRepository;
        this.categoryEnablementService = categoryEnablementService;
        this.lookupService = lookupService;
        this.billGuard = billGuard;
    }

    @GetMapping("/ranges")
    public List<RangeDto> ranges() {
        return rangeService.list().stream().map(RangeDto::from).toList();
    }

    @PostMapping("/ranges")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public RangeDto addRange(@Valid @RequestBody RangeRequest request) {
        return RangeDto.from(rangeService.add(request.minAmount(), request.maxAmount()));
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers(@RequestParam String categoryId) {
        requireEnabled(categoryId);
        return lookupService.billers(categoryId).stream()
                .map(biller -> new ProviderDto(biller.billerId(), biller.name()))
                .toList();
    }

    @GetMapping("/rules")
    public List<CommissionRuleService.RuleView> rules(@RequestParam(required = false) UUID vendorId) {
        return ruleService.list(vendorId);
    }

    @GetMapping("/rules/{id}")
    public CommissionRuleService.RuleDetail rule(@PathVariable UUID id) {
        return ruleService.get(id);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail createRule(
            @Valid @RequestBody CreateRuleRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsCategory category = requireEnabled(request.categoryId());
        BbpsProvider.ProviderBiller provider = lookupService.billers(category.categoryId()).stream()
                .filter(biller -> biller.billerId().equals(request.providerBillerId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found for this category"));
        return ruleService.create(new CommissionRuleService.NewRule(
                category.categoryId(), category.name(),
                provider.billerId(), provider.name(),
                request.rangeId(),
                request.vendors().stream()
                        .map(vendor -> new CommissionRuleService.NewVendor(vendor.vendorClientId(), vendor.commissionPercent()))
                        .toList()), principal.phoneNumber());
    }

    @PostMapping("/rules/{id}/vendors")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail addVendor(
            @PathVariable UUID id, @Valid @RequestBody VendorRateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ruleService.addVendor(id, request.vendorClientId(), request.commissionPercent(), principal.phoneNumber());
    }

    @DeleteMapping("/rules/{id}/vendors/{vendorId}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail removeVendor(
            @PathVariable UUID id, @PathVariable UUID vendorId, @AuthenticationPrincipal AuthenticatedUser principal) {
        billGuard.assertVendorClear(id, vendorId);
        return ruleService.removeVendor(id, vendorId, principal.phoneNumber());
    }

    @PatchMapping("/rules/{id}/vendors/{vendorId}/active")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail setVendorActive(
            @PathVariable UUID id, @PathVariable UUID vendorId, @Valid @RequestBody ActiveRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (!request.active()) {
            billGuard.assertVendorClear(id, vendorId);
        }
        return ruleService.setVendorActive(id, vendorId, request.active(), principal.phoneNumber());
    }

    @PutMapping("/rules/{id}/vendors/{vendorId}/rate")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail enterRate(
            @PathVariable UUID id, @PathVariable UUID vendorId, @Valid @RequestBody RateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ruleService.enterRate(id, vendorId, request.commissionPercent(), principal.phoneNumber());
    }

    @PatchMapping("/rules/{id}/active")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail setActive(
            @PathVariable UUID id, @Valid @RequestBody ActiveRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (!request.active()) {
            billGuard.assertRuleClear(id);
        }
        return ruleService.setActive(id, request.active(), principal.phoneNumber());
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void deleteRule(@PathVariable UUID id) {
        billGuard.assertRuleClear(id);
        ruleService.delete(id);
    }

    @PostMapping("/proposals/{id}/accept")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail accept(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ruleService.accept(id, principal.phoneNumber());
    }

    @PostMapping("/proposals/{id}/reject")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CommissionRuleService.RuleDetail reject(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ruleService.reject(id, principal.phoneNumber());
    }

    private BbpsCategory requireEnabled(String categoryId) {
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryId));
        if (!categoryEnablementService.isOrganisationEnabled(category.categoryId())) {
            throw new BadRequestException("Category '" + category.categoryId() + "' is not enabled for Paytrix");
        }
        return category;
    }

    public record RangeRequest(BigDecimal minAmount, BigDecimal maxAmount) {
    }

    public record RangeDto(UUID id, BigDecimal minAmount, BigDecimal maxAmount, Instant createdAt) {
        static RangeDto from(CommissionAmountRange range) {
            return new RangeDto(range.getId(), range.getMinAmount(), range.getMaxAmount(), range.getCreatedAt());
        }
    }

    public record ProviderDto(String billerId, String name) {
    }

    public record VendorRateRequest(
            @NotNull UUID vendorClientId,
            @DecimalMin("0.001") @DecimalMax("100") BigDecimal commissionPercent) {
    }

    public record CreateRuleRequest(
            @NotBlank String categoryId,
            @NotBlank String providerBillerId,
            @NotNull UUID rangeId,
            @NotEmpty List<@Valid VendorRateRequest> vendors) {
    }

    public record RateRequest(
            @NotNull @DecimalMin("0.001") @DecimalMax("100") BigDecimal commissionPercent) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }
}
