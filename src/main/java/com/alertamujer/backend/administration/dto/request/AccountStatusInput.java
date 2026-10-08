package com.alertamujer.backend.administration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Contract body for an administrative account-state change. */
public record AccountStatusInput(
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status) {
}
