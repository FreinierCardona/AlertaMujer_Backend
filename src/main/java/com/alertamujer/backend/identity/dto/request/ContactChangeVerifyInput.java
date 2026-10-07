package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** OTP proof for an unchanged candidate contact. */
public record ContactChangeVerifyInput(
        @Pattern(regexp = "^(email|phone)$") String field,
        @NotBlank @Size(max = 254) String value,
        @Pattern(regexp = "^[0-9]{6}$") String code) {
}
