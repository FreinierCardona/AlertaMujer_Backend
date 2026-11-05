package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Solicitud pública deliberadamente genérica de OTP para recuperar la contraseña. */
public record PasswordResetOtpRequest(@NotBlank @Email String email) {
}
