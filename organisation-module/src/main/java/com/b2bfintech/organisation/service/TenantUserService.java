package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Role;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Reads/writes users within whichever tenant schema {@link com.b2bfintech.common.tenant.TenantContext}
 * currently points at. Callers (organisation provisioning, and later organisation-admin "manage users"
 * screens) are responsible for setting the tenant context before invoking this service.
 */
@Service
public class TenantUserService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;

    public TenantUserService(AppUserRepository appUserRepository, RoleRepository roleRepository) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
    }

    /** Creates an organisation-side user (no client) in the current tenant schema with the given role. */
    public AppUser createUser(String phoneNumber, String fullName, String roleName) {
        return createUser(phoneNumber, fullName, roleName, null);
    }

    /** Creates a user, optionally attached to a client (CLIENT_ADMIN / CLIENT_OPERATOR). */
    public AppUser createUser(String phoneNumber, String fullName, String roleName, Client client) {
        return createUser(phoneNumber, fullName, roleName, client, null);
    }

    /** Same as above, additionally recording an optional email (login is still resolved via platform.user_directory). */
    public AppUser createUser(String phoneNumber, String fullName, String roleName, Client client, String email) {
        if (appUserRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new BadRequestException("A user with phone number " + phoneNumber + " already exists in this organisation");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

        AppUser user = new AppUser();
        user.setPhoneNumber(phoneNumber);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setClient(client);
        return appUserRepository.save(user);
    }

    public AppUser getByPhoneNumber(String phoneNumber) {
        return appUserRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for phone number " + phoneNumber));
    }

    public AppUser getById(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Updates name/email/role for an existing user. Phone number (the login identifier) is immutable here. */
    public AppUser updateProfile(UUID userId, String fullName, String email, String roleName) {
        AppUser user = getById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        return appUserRepository.save(user);
    }

    public List<AppUser> listAll() {
        return appUserRepository.findAll();
    }

    public List<AppUser> listForClient(UUID clientId) {
        return appUserRepository.findByClientId(clientId);
    }

    public void suspend(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(AppUser.UserStatus.SUSPENDED);
        appUserRepository.save(user);
    }

    public void reactivate(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(AppUser.UserStatus.ACTIVE);
        appUserRepository.save(user);
    }
}
