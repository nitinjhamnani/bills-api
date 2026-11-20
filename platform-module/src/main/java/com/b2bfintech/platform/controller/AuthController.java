package com.b2bfintech.platform.controller;

import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.platform.dto.LoginResponseDto;
import com.b2bfintech.platform.dto.PasswordLoginDto;
import com.b2bfintech.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password sign-in, silent refresh, session verification, and server-side logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/password/login")
    @PreAuthorize("permitAll()")
    public LoginResponseDto loginWithPassword(
            @Valid @RequestBody PasswordLoginDto request,
            HttpServletRequest servletRequest) {
        AuthService.LoginResult result = authService.loginWithPassword(
                request.identifier(),
                request.password(),
                servletRequest.getHeader("User-Agent"),
                extractClientIp(servletRequest));
        return toResponse(result);
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public LoginResponseDto refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal != null && principal.sessionId() != null) {
            authService.logout(principal.sessionId());
        }
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    @GetMapping("/session")
    @PreAuthorize("isAuthenticated()")
    public LoginResponseDto.SessionResponseDto session(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthService.SessionState state = authService.currentSession(principal);
        return new LoginResponseDto.SessionResponseDto(
                state.sessionId().toString(),
                toUserProfile(state.user(), state.portal(), state.role(), state.clientId(), state.branchId())
        );
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public LoginResponseDto.UserProfileDto updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        AuthService.SessionState state = authService.updateProfile(principal, request.fullName(), request.email());
        return toUserProfile(state.user(), state.portal(), state.role(), state.clientId(), state.branchId());
    }

    private static LoginResponseDto toResponse(AuthService.LoginResult result) {
        return new LoginResponseDto(
                result.sessionId().toString(),
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.expiresInSeconds(),
                toUserProfile(result.user(), result.portal(), result.role(), result.clientId(), result.branchId())
        );
    }

    private static LoginResponseDto.UserProfileDto toUserProfile(
            AppUser user, Portal portal, String role, String clientId, String branchId) {
        String clientType = switch (portal) {
            case VENDOR -> "VENDOR";
            case CORPORATE -> "CORPORATE";
            case PLATFORM -> null;
        };
        return new LoginResponseDto.UserProfileDto(
                user.getUserId(),
                user.getPhone(),
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getStatus().name(),
                user.getCreatedOn() != null ? user.getCreatedOn().toString() : null,
                portal.name(),
                user.getUserType().name(),
                role,
                null,
                clientId,
                branchId,
                clientType
        );
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, max = 72) String newPassword) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 150) String email) {
    }
}
