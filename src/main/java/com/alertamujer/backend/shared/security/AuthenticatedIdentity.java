package com.alertamujer.backend.shared.security;

import java.util.UUID;

/** Minimal trusted identity reconstructed from a validated access token and session. */
public record AuthenticatedIdentity(UUID userId, UUID sessionId, String role, boolean termsPending) {
}
