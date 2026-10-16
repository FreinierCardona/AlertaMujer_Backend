package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;

/** Safe v2 response after OTP issuance or a stage transition. */
public record RegistrationStageResponse(String stage, Instant expiresAt, String simulatedPhoneCode) {
}
