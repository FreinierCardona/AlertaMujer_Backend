package com.alertamujer.backend.identity.dto.request;

import com.alertamujer.backend.identity.model.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Datos aceptados al consumir un OTP de un registro pendiente. */
public record OtpVerificationInput(
        @NotNull OtpChannel channel,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
