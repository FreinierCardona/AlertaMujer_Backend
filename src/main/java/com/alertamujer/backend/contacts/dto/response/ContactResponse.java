package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Own relationship plus its current SOS/notification eligibility. */
public record ContactResponse(UUID contactId, String status, Instant expiresAt, boolean eligible) {
}
