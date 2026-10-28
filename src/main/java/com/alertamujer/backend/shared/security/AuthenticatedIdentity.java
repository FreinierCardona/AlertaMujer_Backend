package com.alertamujer.backend.shared.security;

import java.util.UUID;

/** Identidad de confianza mínima reconstruida a partir de un token de acceso y una sesión validados. */
public record AuthenticatedIdentity(UUID userId, UUID sessionId, String role, boolean termsPending) {
}
