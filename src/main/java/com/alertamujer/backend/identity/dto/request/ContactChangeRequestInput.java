package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dato de contacto propuesto; no se escribe en users hasta consumir su OTP. */
public record ContactChangeRequestInput(
        @Pattern(regexp = "^(email|phone)$") String field,
        @NotBlank @Size(max = 254) String value) {
}
