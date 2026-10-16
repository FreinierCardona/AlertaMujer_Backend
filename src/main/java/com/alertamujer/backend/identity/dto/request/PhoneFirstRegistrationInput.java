package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Non-sensitive first step of public registration. */
public record PhoneFirstRegistrationInput(
        @NotBlank @Pattern(regexp = "^@[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])$",
                message = "must be @ followed by 3 to 20 lowercase letters, numbers or underscores")
        String username,
        @NotBlank @Size(max = 100) String firstNames,
        @NotBlank @Size(max = 100) String lastNames,
        @NotBlank @Pattern(regexp = "^3[0-9]{9}$", message = "must be a normalized Colombian mobile number")
        String phone) {
}
