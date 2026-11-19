package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.model.CategoryOperator;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.repository.CategoryOperatorRepository;
import com.b2bfintech.platform.service.CatalogCache;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Organisation settings: synced categories and per-category operator enablement. */
@RestController
@RequestMapping("/api/v1/paytrix/categories")
public class OrganisationCategoryController {

    private final BbpsCategoryRepository categoryRepository;
    private final CategoryOperatorRepository operatorRepository;
    private final CategoryEnablementService enablementService;
    private final BbpsLookupService lookupService;
    private final CatalogCache catalogCache;

    public OrganisationCategoryController(
            BbpsCategoryRepository categoryRepository,
            CategoryOperatorRepository operatorRepository,
            CategoryEnablementService enablementService,
            BbpsLookupService lookupService,
            CatalogCache catalogCache) {
        this.categoryRepository = categoryRepository;
        this.operatorRepository = operatorRepository;
        this.enablementService = enablementService;
        this.lookupService = lookupService;
        this.catalogCache = catalogCache;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<CategorySettingDto> list() {
        Map<String, Boolean> enabled = enablementService.listOrganisation().stream()
                .collect(Collectors.toMap(OrganisationCategoryConfig::getCategoryId, OrganisationCategoryConfig::isEnabled, (a, b) -> a));
        return categoryRepository.findAllActive().stream()
                .map(category -> new CategorySettingDto(
                        category.categoryId(), category.name(), enabled.getOrDefault(category.categoryId(), false)))
                .toList();
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public CategorySettingDto set(@PathVariable String categoryId, @RequestBody EnabledRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsCategory category = resolveCategory(categoryId);
        enablementService.setOrganisationEnabled(category.categoryId(), request.enabled(), principal.phoneNumber());
        return new CategorySettingDto(category.categoryId(), category.name(), request.enabled());
    }

    @GetMapping("/{categoryId}/operators")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<OperatorSettingDto> listOperators(@PathVariable String categoryId) {
        BbpsCategory category = resolveCategory(categoryId);
        if (!operatorRepository.existsByCategoryId(category.id())) {
            lookupService.billers(category.categoryId());
        }
        return operatorRepository.findAllByCategory(category.id()).stream()
                .map(op -> new OperatorSettingDto(
                        op.operatorId(),
                        op.operatorName(),
                        category.categoryId(),
                        category.name(),
                        op.active()))
                .toList();
    }

    @PutMapping("/{categoryId}/operators/{operatorId}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public OperatorSettingDto setOperator(@PathVariable String categoryId,
                                          @PathVariable String operatorId,
                                          @RequestBody EnabledRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsCategory category = resolveCategory(categoryId);
        if (!operatorRepository.existsByCategoryId(category.id())) {
            lookupService.billers(category.categoryId());
        }
        CategoryOperator updated = operatorRepository.setOperatorActive(category.id(), operatorId, request.enabled())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found: " + operatorId));
        catalogCache.evictOperators(category.categoryId());
        if (request.enabled() && !enablementService.isOrganisationEnabled(category.categoryId())) {
            enablementService.setOrganisationEnabled(category.categoryId(), true, principal.phoneNumber());
        }
        return new OperatorSettingDto(
                updated.operatorId(),
                updated.operatorName(),
                category.categoryId(),
                category.name(),
                updated.active());
    }

    private BbpsCategory resolveCategory(String categoryIdOrName) {
        return categoryRepository.findActiveByCategoryId(categoryIdOrName)
                .or(() -> categoryRepository.findAllActive().stream()
                        .filter(cat -> cat.name().equalsIgnoreCase(categoryIdOrName)
                                || cat.categoryId().equalsIgnoreCase(categoryIdOrName))
                        .findFirst())
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryIdOrName));
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record CategorySettingDto(String categoryId, String name, boolean enabled) {
    }

    public record OperatorSettingDto(
            String operatorId,
            String name,
            String categoryId,
            String categoryName,
            boolean enabled) {
    }
}
