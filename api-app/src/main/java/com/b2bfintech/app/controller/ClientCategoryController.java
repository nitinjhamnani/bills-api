package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.ClientCategoryConfig;
import com.b2bfintech.organisation.entity.OrganisationCategoryConfig;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.ClientService;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Categories a client may use: only those the organisation has already enabled. */
@RestController
@RequestMapping("/api/v1/organisation/clients/{clientId}/categories")
public class ClientCategoryController {

    private final BbpsCategoryRepository categoryRepository;
    private final CategoryEnablementService enablementService;
    private final ClientService clientService;

    public ClientCategoryController(
            BbpsCategoryRepository categoryRepository, CategoryEnablementService enablementService, ClientService clientService) {
        this.categoryRepository = categoryRepository;
        this.enablementService = enablementService;
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CLIENT_ADMIN','CLIENT_OPERATOR')")
    public List<ClientCategoryDto> list(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return visible(clientId);
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientCategoryDto set(@PathVariable UUID clientId, @PathVariable String categoryId,
                                 @RequestBody OrganisationCategoryController.EnabledRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsCategory category = categoryRepository.findActiveByCategoryId(categoryId)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categoryId));
        enablementService.setClientEnabled(
                clientService.getById(clientId), category.categoryId(), request.enabled(), principal.phoneNumber());
        return new ClientCategoryDto(category.categoryId(), category.name(), request.enabled());
    }

    private List<ClientCategoryDto> visible(UUID clientId) {
        Set<String> organisationEnabled = enablementService.listOrganisation().stream()
                .filter(OrganisationCategoryConfig::isEnabled)
                .map(OrganisationCategoryConfig::getCategoryId)
                .collect(Collectors.toSet());
        Map<String, Boolean> clientEnabled = enablementService.listClientRows(clientId).stream()
                .collect(Collectors.toMap(ClientCategoryConfig::getCategoryId, ClientCategoryConfig::isEnabled, (a, b) -> a));
        return categoryRepository.findAllActive().stream()
                .filter(category -> organisationEnabled.contains(category.categoryId()))
                .map(category -> new ClientCategoryDto(
                        category.categoryId(), category.name(), clientEnabled.getOrDefault(category.categoryId(), true)))
                .toList();
    }

    public record ClientCategoryDto(String categoryId, String name, boolean enabled) {
    }
}
