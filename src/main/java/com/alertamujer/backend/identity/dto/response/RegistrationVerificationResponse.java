package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Safe state after a registration-channel OTP has been consumed. */
public record RegistrationVerificationResponse(String status, UUID userId) {
}
