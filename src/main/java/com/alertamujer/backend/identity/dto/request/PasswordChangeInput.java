package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Datos de entrada para el cambio de contraseña autenticado. */
public record PasswordChangeInput(
        @NotBlank String currentPassword,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{12,}$") String newPassword) {
}
