package com.b2bfintech.app.service;

import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.TenantUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Paytrix superadmin from environment configuration when none exists.
 */
@Component
public class PlatformAdminPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminPasswordInitializer.class);

    private final AppUserRepository appUserRepository;
    private final TenantUserService tenantUserService;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String phone;
    private final String name;
    private final String password;

    public PlatformAdminPasswordInitializer(
            AppUserRepository appUserRepository,
            TenantUserService tenantUserService,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.platform-admin-email:}") String email,
            @Value("${app.auth.platform-admin-phone:}") String phone,
            @Value("${app.auth.platform-admin-name:Paytrix Admin}") String name,
            @Value("${app.auth.platform-admin-password:}") String password) {
        this.appUserRepository = appUserRepository;
        this.tenantUserService = tenantUserService;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.phone = phone;
        this.name = name;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.existsByUserType(AppUser.UserType.SUPER_ADMIN)
                || appUserRepository.existsByUserType(AppUser.UserType.ORG_SUPERADMIN)) {
            return;
        }
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            log.warn("No Paytrix superadmin exists and PLATFORM_ADMIN_PHONE / PLATFORM_ADMIN_PASSWORD are not set");
            return;
        }
        String trimmedPhone = phone.trim();
        String trimmedEmail = (email == null || email.isBlank()) ? null : email.trim();
        if (appUserRepository.existsByPhone(trimmedPhone)) {
            log.warn("Cannot create Paytrix superadmin; phone {} is already in use", trimmedPhone);
            return;
        }
        if (trimmedEmail != null && appUserRepository.existsByEmail(trimmedEmail)) {
            log.warn("Cannot create Paytrix superadmin; email {} is already in use", trimmedEmail);
            return;
        }
        String displayName = (name == null || name.isBlank()) ? "Paytrix Admin" : name.trim();
        tenantUserService.createSuperAdmin(
                trimmedPhone, trimmedEmail, displayName, passwordEncoder.encode(password));
        log.info("Created Paytrix superadmin (phone={}, email={})", trimmedPhone, trimmedEmail);
    }
}
