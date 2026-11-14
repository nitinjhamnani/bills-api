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
 * Creates the first Paytrix admin from configuration when none exists.
 * Phone, name, and password come from the environment, not from a migration.
 */
@Component
public class PlatformAdminPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminPasswordInitializer.class);

    private final AppUserRepository appUserRepository;
    private final TenantUserService tenantUserService;
    private final PasswordEncoder passwordEncoder;
    private final String phone;
    private final String name;
    private final String password;

    public PlatformAdminPasswordInitializer(
            AppUserRepository appUserRepository,
            TenantUserService tenantUserService,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.platform-admin-phone:}") String phone,
            @Value("${app.auth.platform-admin-name:Paytrix Admin}") String name,
            @Value("${app.auth.platform-admin-password:}") String password) {
        this.appUserRepository = appUserRepository;
        this.tenantUserService = tenantUserService;
        this.passwordEncoder = passwordEncoder;
        this.phone = phone;
        this.name = name;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.existsByUserType(AppUser.UserType.ORG_SUPERADMIN)) {
            return;
        }
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            log.warn("No Paytrix admin exists and PLATFORM_ADMIN_PHONE / PLATFORM_ADMIN_PASSWORD are not set");
            return;
        }
        if (appUserRepository.existsByPhone(phone.trim())) {
            log.warn("Cannot create Paytrix admin; phone {} is already in use", phone.trim());
            return;
        }
        String displayName = name == null || name.isBlank() ? "Paytrix Admin" : name.trim();
        tenantUserService.createSuperAdmin(phone.trim(), displayName, passwordEncoder.encode(password));
        log.info("Created Paytrix admin {}", phone.trim());
    }
}
