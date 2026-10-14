package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BbpsLookupService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.organisation.service.CategoryEnablementService;
import com.b2bfintech.organisation.service.LocationEnablementService;
import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import com.b2bfintech.platform.repository.BbpsLocationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** What a client sees while creating a bill: enabled categories, locations, then live billers and their fields. */
@RestController
@RequestMapping("/api/v1/client/bbps")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
public class ClientBbpsController {

    private final BbpsCategoryRepository categoryRepository;
    private final BbpsLocationRepository locationRepository;
    private final CategoryEnablementService enablementService;
    private final LocationEnablementService locationEnablementService;
    private final BbpsLookupService lookupService;

    public ClientBbpsController(
            BbpsCategoryRepository categoryRepository, BbpsLocationRepository locationRepository,
            CategoryEnablementService enablementService, LocationEnablementService locationEnablementService,
            BbpsLookupService lookupService) {
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.enablementService = enablementService;
        this.locationEnablementService = locationEnablementService;
        this.lookupService = lookupService;
    }

    @GetMapping("/categories")
    public List<ClientCategoryController.ClientCategoryDto> categories(@AuthenticationPrincipal AuthenticatedUser principal) {
        UUID clientId = requiredClient(principal);
        return categoryRepository.findAllActive().stream()
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

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return locationRepository.findAllActive().stream()
                .filter(location -> locationEnablementService.isEnabled(location.locationId()))
                .map(location -> new LocationDto(location.locationId(), location.name(), location.abbreviation()))
                .toList();
    }

    @GetMapping("/billers")
    public List<BillerDto> billers(@RequestParam String categoryId, @RequestParam String locationId,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        enablementService.assertEnabled(requiredClient(principal), categoryId);
        locationEnablementService.assertEnabled(locationId);
        return lookupService.billers(categoryId, locationId).stream()
                .map(biller -> new BillerDto(biller.billerId(), biller.name(), biller.categoryId(), biller.locationId()))
                .toList();
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

    private static UUID requiredClient(AuthenticatedUser principal) {
        if (principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This action is only available to a client user");
        }
        UUID clientId = UUID.fromString(principal.clientId());
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return clientId;
    }

    public record LocationDto(String locationId, String name, String abbreviation) {
    }

    public record BillerDto(String billerId, String name, String categoryId, String locationId) {
    }

    public record ParameterDto(String name, String label, String dataType, String validationRegex, String errorMessage) {
    }
}
