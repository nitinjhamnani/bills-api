package com.b2bfintech.platform.controller;

import com.b2bfintech.platform.dto.LoginResponseDto;
import com.b2bfintech.platform.dto.PasswordLoginDto;
import com.b2bfintech.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password login shared by all three modules — the caller's role (returned in the
 * token/response) determines which module's screens the frontend routes them into.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Phone/email + password. Issues a JWT on success. {@code identifier} may be the phone
     * number or email.
     */
    @PostMapping("/password/login")
    public LoginResponseDto loginWithPassword(@Valid @RequestBody PasswordLoginDto request) {
        return toResponse(authService.loginWithPassword(request.identifier(), request.password()));
    }

    private LoginResponseDto toResponse(AuthService.LoginResult result) {
        var user = result.user();
        return new LoginResponseDto(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                new LoginResponseDto.UserProfileDto(
                        user.userId().toString(), user.phoneNumber(), user.displayName(), user.role(), user.tenantSchema(),
                        user.clientId() == null ? null : user.clientId().toString())
        );
    }
}
