package com.alertamujer.backend.identity.dto.request;

import com.alertamujer.backend.identity.model.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** OTP input constrained to the public v2 registration stages by the service. */
public record V2OtpVerificationInput(
        @NotNull OtpChannel channel,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
