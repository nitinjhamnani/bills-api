package com.b2bfintech.platform.dto;

public record LoginResponseDto(
        String sessionId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileDto user
) {
    public record UserProfileDto(
            String userId,
            String phoneNumber,
            String userCode,
            String displayName,
            String portal,
            String userType,
            String role,
            String tenantSchema,
            String clientId,
            String branchId,
            String clientType
    ) {
    }

    public record SessionResponseDto(
            String sessionId,
            UserProfileDto user
    ) {
    }
}
