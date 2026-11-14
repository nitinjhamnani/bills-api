package com.b2bfintech.organisation.service;

import com.b2bfintech.common.security.UserCodeLookup;
import com.b2bfintech.organisation.repository.AppUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AppUserCodeLookup implements UserCodeLookup {

    private final AppUserRepository appUserRepository;

    public AppUserCodeLookup(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public Optional<String> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return appUserRepository.findById(userId).map(user -> user.getUserId());
    }
}
