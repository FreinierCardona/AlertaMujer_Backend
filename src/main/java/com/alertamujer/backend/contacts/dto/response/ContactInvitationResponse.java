package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Estado seguro de la relación; nunca contiene los datos de contacto de la otra persona. */
public record ContactInvitationResponse(UUID contactId, String status, Instant expiresAt) {
}
