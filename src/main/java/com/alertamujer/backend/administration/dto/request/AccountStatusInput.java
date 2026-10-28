package com.alertamujer.backend.administration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo definido por el contrato para cambiar el estado administrativo de una cuenta. */
public record AccountStatusInput(
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status) {
}
