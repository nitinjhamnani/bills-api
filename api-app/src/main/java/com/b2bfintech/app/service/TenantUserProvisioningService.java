package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Pairs {@code TenantUserService} (organisation-module: creates the app_user row in the current
 * tenant schema) with {@code UserDirectoryRepository} (platform-module: registers the phone
 * number -> tenant mapping so the new user can log in). Lives in api-app because it's the one
 * module allowed to depend on both platform-module and organisation-module for this kind of
 * cross-cutting orchestration - see the implementation plan's module-boundary notes.
 */
@Service
public class TenantUserProvisioningService {

    private final TenantUserService tenantUserService;
    private final UserDirectoryRepository userDirectoryRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantUserProvisioningService(
            TenantUserService tenantUserService, UserDirectoryRepository userDirectoryRepository, PasswordEncoder passwordEncoder) {
        this.tenantUserService = tenantUserService;
        this.userDirectoryRepository = userDirectoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an organisation-side user (no client) with phone login — no email/password on file. */
    @Transactional
    public AppUser createOrganisationUser(String phoneNumber, String fullName, String roleName) {
        return createOrganisationUser(phoneNumber, fullName, roleName, null, null);
    }

    /**
     * Same as above, additionally registering an optional email + initial password so the user can
     * log in with phone/email + password (implementation plan &sect;8.1 extension).
     */
    @Transactional
    public AppUser createOrganisationUser(
            String phoneNumber, String fullName, String roleName, String email, String rawPassword) {
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && userDirectoryRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("A user with email " + normalizedEmail + " already exists");
        }
        AppUser user = tenantUserService.createUser(phoneNumber, fullName, roleName, null, normalizedEmail);
        String passwordHash = hashOrNull(rawPassword);
        userDirectoryRepository.insert(
                user.getId(), phoneNumber, TenantContext.getCurrentTenant(), roleName, fullName, null,
                normalizedEmail, passwordHash);
        return user;
    }

    /** Creates a client-side user and registers it for login. Runs inside the caller's current tenant. */
    public AppUser createClientUser(String phoneNumber, String fullName, String roleName, Client client) {
        return createClientUser(phoneNumber, fullName, roleName, client, null, null);
    }

    /**
     * Same as above, additionally registering an optional email + password so the client admin can
     * log in with phone/email + password (implementation plan &sect;8.1 extension).
     */
    @Transactional
    public AppUser createClientUser(
            String phoneNumber, String fullName, String roleName, Client client, String email, String rawPassword) {
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && userDirectoryRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("A user with email " + normalizedEmail + " already exists");
        }
        AppUser user = tenantUserService.createUser(phoneNumber, fullName, roleName, client, normalizedEmail);
        String passwordHash = hashOrNull(rawPassword);
        userDirectoryRepository.insert(
                user.getId(), phoneNumber, TenantContext.getCurrentTenant(), roleName, fullName, client.getId(),
                normalizedEmail, passwordHash);
        return user;
    }

    /** Updates a user's name/email/role, keeping platform.user_directory (the login/JWT source of truth) in sync. */
    @Transactional
    public AppUser updateUserProfile(UUID userId, String fullName, String email, String roleName) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && userDirectoryRepository.existsByEmailExcludingUser(normalizedEmail, userId)) {
            throw new BadRequestException("A user with email " + normalizedEmail + " already exists");
        }
        AppUser user = tenantUserService.updateProfile(userId, fullName, normalizedEmail, roleName);
        userDirectoryRepository.updateProfile(userId, fullName, roleName, normalizedEmail);
        return user;
    }

    /** Sets or replaces a user's password, enabling/updating their phone/email + password login. */
    @Transactional
    public void setUserPassword(UUID userId, String rawPassword) {
        userDirectoryRepository.updatePasswordHash(userId, passwordEncoder.encode(rawPassword));
    }

    /**
     * Suspends a user in both the tenant schema (drives most UI listings) and platform.user_directory
     * (what login actually checks) - updating only the former would leave a "suspended" user still
     * able to sign in.
     */
    @Transactional
    public void suspendUser(UUID userId) {
        tenantUserService.suspend(userId);
        userDirectoryRepository.updateStatus(userId, "SUSPENDED");
    }

    @Transactional
    public void reactivateUser(UUID userId) {
        tenantUserService.reactivate(userId);
        userDirectoryRepository.updateStatus(userId, "ACTIVE");
    }

    /** Login phones are unique across the platform, not just the current organisation. */
    private void assertPhoneAvailable(String phoneNumber) {
        if (userDirectoryRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BadRequestException("A user with phone number " + phoneNumber + " already exists");
        }
    }

    private String normalizeEmail(String email) {
        // Blank/empty string (as opposed to Java null) is still a real, distinct value to Postgres's
        // UNIQUE constraint, so a second user with no email would otherwise collide on "".
        return (email == null || email.isBlank()) ? null : email.trim();
    }

    private String hashOrNull(String rawPassword) {
        return (rawPassword == null || rawPassword.isBlank()) ? null : passwordEncoder.encode(rawPassword);
    }
}
