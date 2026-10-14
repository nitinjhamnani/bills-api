package com.b2bfintech.app.controller;

import com.b2bfintech.app.controller.PlatformOrganisationDtos.CreateAdminRequest;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationDetailDto;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationUserDetailDto;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.SetPasswordRequest;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.UpdateStatusRequest;
import com.b2bfintech.app.service.PlatformOrganisationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/organisations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformOrganisationController {

    private final PlatformOrganisationService organisationService;

    public PlatformOrganisationController(PlatformOrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @GetMapping("/{id}")
    public OrganisationDetailDto getOrganisation(@PathVariable UUID id) {
        return organisationService.getDetail(id);
    }

    @PatchMapping("/{id}/status")
    public OrganisationDetailDto updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return organisationService.updateStatus(id, request.status());
    }

    @GetMapping("/{id}/users/{userId}")
    public OrganisationUserDetailDto getUser(@PathVariable UUID id, @PathVariable UUID userId) {
        return organisationService.getUser(id, userId);
    }

    @PostMapping("/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganisationUserDetailDto createAdmin(@PathVariable UUID id, @RequestBody CreateAdminRequest request) {
        return organisationService.createAdmin(id, request);
    }

    @PatchMapping("/{id}/users/{userId}/status")
    public OrganisationUserDetailDto updateUserStatus(
            @PathVariable UUID id, @PathVariable UUID userId, @RequestBody UpdateStatusRequest request) {
        return organisationService.updateUserStatus(id, userId, request.status());
    }

    @PatchMapping("/{id}/users/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserPassword(
            @PathVariable UUID id, @PathVariable UUID userId, @RequestBody SetPasswordRequest request) {
        organisationService.setUserPassword(id, userId, request.password());
    }
}
