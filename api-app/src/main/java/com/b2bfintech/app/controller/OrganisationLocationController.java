package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.OrganisationLocationConfig;
import com.b2bfintech.organisation.service.LocationEnablementService;
import com.b2bfintech.platform.model.BbpsLocation;
import com.b2bfintech.platform.repository.BbpsLocationRepository;
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

/** Organisation settings: every synced location, disabled until an admin switches it on. */
@RestController
@RequestMapping("/api/v1/organisation/locations")
public class OrganisationLocationController {

    private final BbpsLocationRepository locationRepository;
    private final LocationEnablementService enablementService;

    public OrganisationLocationController(BbpsLocationRepository locationRepository, LocationEnablementService enablementService) {
        this.locationRepository = locationRepository;
        this.enablementService = enablementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<LocationSettingDto> list() {
        Map<String, Boolean> enabled = enablementService.list().stream()
                .collect(Collectors.toMap(OrganisationLocationConfig::getLocationId, OrganisationLocationConfig::isEnabled, (a, b) -> a));
        return locationRepository.findAllActive().stream()
                .map(location -> toDto(location, enabled.getOrDefault(location.locationId(), false)))
                .toList();
    }

    @PutMapping("/{locationId}")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public LocationSettingDto set(@PathVariable String locationId, @RequestBody EnabledRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        BbpsLocation location = locationRepository.findActiveByLocationId(locationId)
                .orElseThrow(() -> new BadRequestException("Unknown location: " + locationId));
        enablementService.setEnabled(location.locationId(), request.enabled(), principal.phoneNumber());
        return toDto(location, request.enabled());
    }

    private static LocationSettingDto toDto(BbpsLocation location, boolean enabled) {
        return new LocationSettingDto(location.locationId(), location.name(), location.abbreviation(), enabled);
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record LocationSettingDto(String locationId, String name, String abbreviation, boolean enabled) {
    }
}
