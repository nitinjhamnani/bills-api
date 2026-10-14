package com.b2bfintech.platform.dto;

public record LoginResponseDto(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileDto user
) {
    public record UserProfileDto(
            String userId,
            String phoneNumber,
            String displayName,
            String role,
            String tenantSchema,
            String clientId
    ) {
    }
}
