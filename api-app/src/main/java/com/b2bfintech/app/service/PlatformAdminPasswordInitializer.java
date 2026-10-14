package com.b2bfintech.app.service;

import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates the first platform admin from configuration when none exists.
 * Phone, name, and password come from the environment, not from a migration.
 */
@Component
public class PlatformAdminPasswordInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminPasswordInitializer.class);
    private static final String ROLE = "PLATFORM_ADMIN";

    private final UserDirectoryRepository userDirectoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final String phone;
    private final String name;
    private final String password;

    public PlatformAdminPasswordInitializer(
            UserDirectoryRepository userDirectoryRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.platform-admin-phone:}") String phone,
            @Value("${app.auth.platform-admin-name:Platform Admin}") String name,
            @Value("${app.auth.platform-admin-password:}") String password) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.phone = phone;
        this.name = name;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userDirectoryRepository.existsByRole(ROLE)) {
            return;
        }
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            log.warn("No platform admin exists and PLATFORM_ADMIN_PHONE / PLATFORM_ADMIN_PASSWORD are not set");
            return;
        }
        if (userDirectoryRepository.existsByPhoneNumber(phone.trim())) {
            log.warn("Cannot create platform admin; phone {} is already in use", phone.trim());
            return;
        }
        String displayName = name == null || name.isBlank() ? "Platform Admin" : name.trim();
        userDirectoryRepository.insert(
                UUID.randomUUID(),
                phone.trim(),
                null,
                ROLE,
                displayName,
                null,
                null,
                passwordEncoder.encode(password));
        log.info("Created platform admin {}", phone.trim());
    }
}
