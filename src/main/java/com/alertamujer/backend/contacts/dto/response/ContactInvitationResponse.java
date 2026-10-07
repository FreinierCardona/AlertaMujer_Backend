package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Safe relationship state; it never contains the other person's contact data. */
public record ContactInvitationResponse(UUID contactId, String status, Instant expiresAt) {
}
