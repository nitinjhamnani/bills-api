package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.platform.repository.OrganisationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the whole Phase 0 chain end to end: JWT -&gt; tenant schema resolved -&gt; Hibernate
 * routes the connection's search_path to that schema -&gt; the tenant's own {@code app_user} row
 * is read back (not just trusted from the token), alongside the organisation's platform-schema
 * registry entry looked up by tenant schema.
 */
@RestController
@RequestMapping("/api/v1/organisation")
public class MeController {

    private final AppUserRepository appUserRepository;
    private final OrganisationRepository organisationRepository;

    public MeController(AppUserRepository appUserRepository, OrganisationRepository organisationRepository) {
        this.appUserRepository = appUserRepository;
        this.organisationRepository = organisationRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CLIENT_ADMIN','CLIENT_OPERATOR')")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser user = appUserRepository.findByPhoneNumber(principal.phoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("User not found in tenant schema"));
        var organisation = organisationRepository.findByTenantSchema(principal.tenantSchema())
                .orElseThrow(() -> new ResourceNotFoundException("Organisation not found for this tenant"));

        return new MeResponse(
                user.getId().toString(), user.getFullName(), user.getPhoneNumber(),
                user.getRole().getName(), organisation.organisationName(), organisation.organisationCode());
    }

    public record MeResponse(
            String userId, String fullName, String phoneNumber,
            String role, String organisationName, String organisationCode) {
    }
}
