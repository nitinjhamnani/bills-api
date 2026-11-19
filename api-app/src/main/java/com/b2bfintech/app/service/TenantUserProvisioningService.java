package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.platform.service.AuthSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and manages users across Platform, Corporates, and Vendors, enforcing BCrypt passwords
 * and revoking active sessions immediately on suspension, password reset, or role changes.
 */
@Service
public class TenantUserProvisioningService {

    private final TenantUserService tenantUserService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    @Autowired
    public TenantUserProvisioningService(
            TenantUserService tenantUserService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService) {
        this.tenantUserService = tenantUserService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    public TenantUserProvisioningService(
            TenantUserService tenantUserService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this(tenantUserService, appUserRepository, passwordEncoder, null);
    }

    @Transactional
    public AppUser createOrganisationUser(
            String phoneNumber, String fullName, String roleName, String email, String rawPassword) {
        String passwordHash = requireAndHashPassword(rawPassword);
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, null);
        return recordCreator(tenantUserService.createUser(
                phoneNumber, fullName, roleName, null, normalizedEmail, passwordHash));
    }

    @Transactional
    public AppUser createClientUser(
            String phoneNumber, String fullName, String roleName, Client client, String email, String rawPassword) {
        String passwordHash = requireAndHashPassword(rawPassword);
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, null);
        return recordCreator(tenantUserService.createUser(
                phoneNumber, fullName, roleName, client, normalizedEmail, passwordHash));
    }

    private AppUser recordCreator(AppUser user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)
                || principal.userId() == null) {
            return user;
        }
        return appUserRepository.findById(principal.userId())
                .map(creator -> {
                    user.setCreatedById(creator.getUserId());
                    user.setCreatedByType(creator.getUserType().name());
                    return appUserRepository.save(user);
                })
                .orElse(user);
    }

    @Transactional
    public AppUser updateUserProfile(String userId, String fullName, String email, String roleName) {
        AppUser before = tenantUserService.getById(userId);
        AppUser.UserType previousType = before.getUserType();
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, userId);
        AppUser updated = tenantUserService.updateProfile(userId, fullName, normalizedEmail, roleName);
        if (previousType != updated.getUserType() && authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "ROLE_CHANGED");
        }
        return updated;
    }

    @Transactional
    public void setUserPassword(String userId, String rawPassword) {
        String passwordHash = requireAndHashPassword(rawPassword);
        tenantUserService.setPassword(userId, passwordHash);
        if (authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "PASSWORD_CHANGED");
        }
    }

    @Transactional
    public void suspendUser(String userId) {
        tenantUserService.suspend(userId);
        if (authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "USER_SUSPENDED");
        }
    }

    @Transactional
    public void reactivateUser(String userId) {
        tenantUserService.reactivate(userId);
    }

    private String requireAndHashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadRequestException("Password is required");
        }
        if (rawPassword.length() < 6 || rawPassword.length() > 72) {
            throw new BadRequestException("Password must be 6 to 72 characters");
        }
        return passwordEncoder.encode(rawPassword);
    }

    private void assertPhoneAvailable(String phoneNumber) {
        if (appUserRepository.existsByPhone(phoneNumber)) {
            throw new BadRequestException("A user with phone number " + phoneNumber + " already exists");
        }
    }

    private void assertEmailAvailable(String email, String exceptUserId) {
        if (email == null) {
            return;
        }
        boolean taken = exceptUserId == null
                ? appUserRepository.existsByEmail(email)
                : appUserRepository.existsByEmailAndUserIdNot(email, exceptUserId);
        if (taken) {
            throw new BadRequestException("A user with email " + email + " already exists");
        }
    }

    private String normalizeEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.trim();
    }
}
