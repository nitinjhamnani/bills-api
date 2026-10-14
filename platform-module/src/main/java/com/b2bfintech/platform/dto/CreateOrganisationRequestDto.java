package com.b2bfintech.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrganisationRequestDto(
        @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{2,29}", message = "3-30 letters/digits/underscores, starting with a letter")
        String organisationCode,
        @NotBlank String organisationName,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit Indian mobile number")
        String adminPhoneNumber,
        @NotBlank String adminName
) {
}
