package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.app.service.BillerListQuery;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.platform.model.BbpsCategory;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.service.CatalogCache;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** What a client sees while creating a bill: enabled categories, then the operators and fields for the one they pick. */
@RestController
@RequestMapping("/api/v1/client/bbps")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR','BRANCH_USER')")
public class ClientBbpsController {

    private final BbpsCategoryRepository categoryRepository;
    private final CatalogCache catalogCache;
    private final CategoryEnablementService enablementService;
    private final BbpsLookupService lookupService;

    public ClientBbpsController(
            BbpsCategoryRepository categoryRepository,
            CatalogCache catalogCache,
            CategoryEnablementService enablementService,
            BbpsLookupService lookupService) {
        this.categoryRepository = categoryRepository;
        this.catalogCache = catalogCache;
        this.enablementService = enablementService;
        this.lookupService = lookupService;
    }

    @GetMapping("/categories")
    public List<ClientCategoryController.ClientCategoryDto> categories(@AuthenticationPrincipal AuthenticatedUser principal) {
        String clientId = requiredClient(principal);
        return activeCategories().stream()
                .filter(category -> {
                    try {
                        enablementService.assertEnabled(clientId, category.categoryId());
                        return true;
                    } catch (BadRequestException ex) {
                        return false;
                    }
                })
                .map(category -> new ClientCategoryController.ClientCategoryDto(category.categoryId(), category.name(), true))
                .toList();
    }

    @GetMapping("/billers")
    public BillerListDto billers(@RequestParam String categoryId,
                                 @RequestParam(required = false) String q,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        enablementService.assertEnabled(requiredClient(principal), categoryId);
        List<BillerDto> operators = lookupService.billers(categoryId).stream()
                .map(biller -> new BillerDto(biller.billerId(), biller.name(), biller.categoryId()))
                .toList();
        BillerListQuery.Page<BillerDto> page = BillerListQuery.page(operators, q, BillerDto::name);
        return new BillerListDto(page.billers(), page.searchRequired());
    }

    @GetMapping("/billers/{billerId}/parameters")
    public List<ParameterDto> parameters(@PathVariable String billerId, @RequestParam String categoryId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        enablementService.assertEnabled(requiredClient(principal), categoryId);
        List<BbpsProvider.ProviderBillerParameter> parameters = lookupService.parameters(categoryId, billerId);
        if (parameters.isEmpty()) {
            throw new BadRequestException("This biller does not publish any bill fields, so a bill cannot be fetched");
        }
        return parameters.stream()
                .map(parameter -> new ParameterDto(
                        parameter.name(), parameter.label(), parameter.dataType(),
                        parameter.validationRegex(), parameter.errorMessage()))
                .toList();
    }

    private List<BbpsCategory> activeCategories() {
        List<BbpsCategory> categories = catalogCache.categories();
        if (!categories.isEmpty()) {
            return categories;
        }
        return categoryRepository.findAllActive();
    }

    private static String requiredClient(AuthenticatedUser principal) {
        if (principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This action is only available to a client user");
        }
        ClientScopeGuard.assertCanAccess(principal, principal.clientId());
        return principal.clientId();
    }

    public record BillerListDto(List<BillerDto> billers, boolean searchRequired) {
    }

    public record BillerDto(String billerId, String name, String categoryId) {
    }

    public record ParameterDto(String name, String label, String dataType, String validationRegex, String errorMessage) {
    }
}
