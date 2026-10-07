package com.alertamujer.backend.identity.dto.request;

import com.alertamujer.backend.identity.model.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Input accepted when consuming an OTP for a pending registration. */
public record OtpVerificationInput(
        @NotNull OtpChannel channel,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
