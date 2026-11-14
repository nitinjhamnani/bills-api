package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.UserTypes;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.util.UserCodeGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

/** Creates and reads people. A role name from the current screens is stored as a user type. */
@Service
public class TenantUserService {

    private final AppUserRepository appUserRepository;

    public TenantUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser createUser(String phoneNumber, String fullName, String roleName) {
        return createUser(phoneNumber, fullName, roleName, null, null, null);
    }

    public AppUser createUser(String phoneNumber, String fullName, String roleName, Client client) {
        return createUser(phoneNumber, fullName, roleName, client, null, null);
    }

    public AppUser createUser(String phoneNumber, String fullName, String roleName, Client client, String email) {
        return createUser(phoneNumber, fullName, roleName, client, email, null);
    }

    public AppUser createUser(
            String phoneNumber, String fullName, String roleName, Client client, String email, String passwordHash) {
        if (appUserRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new BadRequestException("A user with phone number " + phoneNumber + " already exists in this organisation");
        }
        AppUser user = new AppUser();
        user.setUserId(generateUniqueUserId());
        user.setPhone(phoneNumber);
        user.setName(fullName);
        user.setEmail(email);
        user.setUserType(UserTypes.fromLegacyRole(roleName, client == null ? null : client.getClientType()));
        user.setUserOrgId(UserTypes.organisationId(client));
        user.setPassword(passwordHash == null ? "" : passwordHash);
        user.setActive(true);
        return appUserRepository.save(user);
    }

    /** The first Paytrix account. Later Paytrix admins are {@link AppUser.UserType#ORG_ADMIN}. */
    public AppUser createSuperAdmin(String phoneNumber, String fullName, String passwordHash) {
        AppUser user = new AppUser();
        user.setUserId(generateUniqueUserId());
        user.setPhone(phoneNumber);
        user.setName(fullName);
        user.setUserType(AppUser.UserType.ORG_SUPERADMIN);
        user.setUserOrgId(AppUser.PAYTRIX);
        user.setCreatedByType("ADMIN");
        user.setPassword(passwordHash);
        user.setActive(true);
        return appUserRepository.save(user);
    }

    public AppUser getByPhoneNumber(String phoneNumber) {
        return appUserRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for phone number " + phoneNumber));
    }

    public AppUser getById(String userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public AppUser updateProfile(String userId, String fullName, String email, String roleName) {
        AppUser user = getById(userId);
        user.setName(fullName);
        user.setEmail(email);
        user.setUserType(UserTypes.fromLegacyRole(roleName, clientType(user)));
        return appUserRepository.save(user);
    }

    public List<AppUser> listAll() {
        return appUserRepository.findAll();
    }

    public List<AppUser> listForClient(String clientId) {
        return appUserRepository.findByUserOrgId(clientId);
    }

    public void suspend(String userId) {
        AppUser user = getById(userId);
        user.setActive(false);
        appUserRepository.save(user);
    }

    public void reactivate(String userId) {
        AppUser user = getById(userId);
        user.setActive(true);
        appUserRepository.save(user);
    }

    public void setPassword(String userId, String passwordHash) {
        AppUser user = getById(userId);
        user.setPassword(passwordHash);
        appUserRepository.save(user);
    }

    private Client.ClientType clientType(AppUser user) {
        if (user.hasUserType(AppUser.UserType.VENDOR_ADMIN) || user.hasUserType(AppUser.UserType.VENDOR_USER)) {
            return Client.ClientType.VENDOR;
        }
        if (user.isPaytrix()) {
            return null;
        }
        return Client.ClientType.CORPORATE;
    }

    private String generateUniqueUserId() {
        String code;
        int attempts = 0;
        do {
            code = UserCodeGenerator.generate();
            attempts++;
        } while (appUserRepository.existsById(code) && attempts < 20);
        if (appUserRepository.existsById(code)) {
            throw new BadRequestException("Could not assign a user code");
        }
        return code;
    }
}
