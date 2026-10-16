package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

/** The opaque proof is returned once and is never persisted in plain text. */
public record PhoneFirstRegistrationResponse(
        UUID registrationRequestId,
        String stage,
        String registrationProof,
        Instant expiresAt,
        String simulatedPhoneCode) {
}
