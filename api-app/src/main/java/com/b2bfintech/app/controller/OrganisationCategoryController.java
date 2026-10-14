package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
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

/** Organisation settings: every synced category, disabled until an admin switches it on. */
@RestController
@RequestMapping("/api/v1/organisation/categories")
public class OrganisationCategoryController {

    private final BbpsCategoryRepository categoryRepository;
    private final CategoryEnablementService enablementService;

    public OrganisationCategoryController(BbpsCategoryRepository categoryRepository, CategoryEnablementService enablementService) {
        this.categoryRepository = categoryRepository;
        this.enablementService = enablementService;
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
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryId));
        enablementService.setOrganisationEnabled(category.categoryId(), request.enabled(), principal.phoneNumber());
        return new CategorySettingDto(category.categoryId(), category.name(), request.enabled());
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record CategorySettingDto(String categoryId, String name, boolean enabled) {
    }
}
