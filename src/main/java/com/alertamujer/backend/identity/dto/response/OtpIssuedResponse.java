package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;

/**
 * The SMS value exists only for the approved academic simulation. It remains
 * null for EMAIL and is never persisted in plain text.
 */
public record OtpIssuedResponse(Instant expiresAt, String simulatedSmsCode) {
}
