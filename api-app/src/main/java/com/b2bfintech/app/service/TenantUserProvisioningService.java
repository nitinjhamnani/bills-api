package com.b2bfintech.app.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a person and stores the password on the same row. Login reads that row.
 */
@Service
public class TenantUserProvisioningService {

    private final TenantUserService tenantUserService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantUserProvisioningService(
            TenantUserService tenantUserService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantUserService = tenantUserService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser createOrganisationUser(String phoneNumber, String fullName, String roleName) {
        return createOrganisationUser(phoneNumber, fullName, roleName, null, null);
    }

    @Transactional
    public AppUser createOrganisationUser(
            String phoneNumber, String fullName, String roleName, String email, String rawPassword) {
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, null);
        return recordCreator(tenantUserService.createUser(
                phoneNumber, fullName, roleName, null, normalizedEmail, hashOrNull(rawPassword)));
    }

    public AppUser createClientUser(String phoneNumber, String fullName, String roleName, Client client) {
        return createClientUser(phoneNumber, fullName, roleName, client, null, null);
    }

    @Transactional
    public AppUser createClientUser(
            String phoneNumber, String fullName, String roleName, Client client, String email, String rawPassword) {
        assertPhoneAvailable(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, null);
        return recordCreator(tenantUserService.createUser(
                phoneNumber, fullName, roleName, client, normalizedEmail, hashOrNull(rawPassword)));
    }

    /** created_by_type is the signed-in creator's user type, and created_by_id their user id. */
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
        String normalizedEmail = normalizeEmail(email);
        assertEmailAvailable(normalizedEmail, userId);
        return tenantUserService.updateProfile(userId, fullName, normalizedEmail, roleName);
    }

    @Transactional
    public void setUserPassword(String userId, String rawPassword) {
        tenantUserService.setPassword(userId, passwordEncoder.encode(rawPassword));
    }

    @Transactional
    public void suspendUser(String userId) {
        tenantUserService.suspend(userId);
    }

    @Transactional
    public void reactivateUser(String userId) {
        tenantUserService.reactivate(userId);
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

    private String hashOrNull(String rawPassword) {
        return (rawPassword == null || rawPassword.isBlank()) ? null : passwordEncoder.encode(rawPassword);
    }
}
