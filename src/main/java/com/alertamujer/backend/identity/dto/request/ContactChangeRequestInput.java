package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Candidate contact; it is not written to users until its OTP is consumed. */
public record ContactChangeRequestInput(
        @Pattern(regexp = "^(email|phone)$") String field,
        @NotBlank @Size(max = 254) String value) {
}
