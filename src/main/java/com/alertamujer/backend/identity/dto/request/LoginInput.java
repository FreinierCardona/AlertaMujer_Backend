package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Credenciales públicas aceptadas por el contrato de inicio de sesión. */
public record LoginInput(@NotBlank String identifier, @NotBlank String password) {
}
