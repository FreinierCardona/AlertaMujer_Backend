package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** A deliberately generic public password-reset OTP request. */
public record PasswordResetOtpRequest(@NotBlank @Email String email) {
}
