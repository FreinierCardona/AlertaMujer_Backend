package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Datos para completar la recuperación de contraseña; el OTP nunca se conserva en una respuesta. */
public record PasswordResetConfirmInput(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{12,}$") String newPassword) {
}
