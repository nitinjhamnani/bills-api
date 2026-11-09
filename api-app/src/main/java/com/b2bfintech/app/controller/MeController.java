package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.repository.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current Paytrix or client user, read from {@code app_user} rather than trusted only from the token. */
@RestController
@RequestMapping("/api/v1/paytrix")
public class MeController {

    private final AppUserRepository appUserRepository;

    public MeController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','SALES','CLIENT_ADMIN','CLIENT_OPERATOR')")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser user = appUserRepository.findByPhoneNumber(principal.phoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return new MeResponse(
                user.getId().toString(), user.getFullName(), user.getPhoneNumber(),
                user.getRole().getName(), "Paytrix", "paytrix");
    }

    public record MeResponse(
            String userId, String fullName, String phoneNumber,
            String role, String organisationName, String organisationCode) {
    }
}
