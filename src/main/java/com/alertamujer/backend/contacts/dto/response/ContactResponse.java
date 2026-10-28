package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Relación propia, presentación segura de la contraparte y acciones autorizadas por el servidor. */
public record ContactResponse(
        UUID contactId,
        String status,
        Instant expiresAt,
        boolean eligible,
        Counterpart counterpart,
        Direction direction,
        List<Action> allowedActions) {

    public record Counterpart(String username, String firstNames, String lastNames) { }

    public enum Direction { SENT, RECEIVED }

    public enum Action { ACCEPT, REJECT, REINVITE }
}
