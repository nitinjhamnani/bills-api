package com.b2bfintech.platform.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code identifier} is either the phone number or the email registered against the account. */
public record PasswordLoginDto(
        @NotBlank String identifier,
        @NotBlank String password
) {
}
