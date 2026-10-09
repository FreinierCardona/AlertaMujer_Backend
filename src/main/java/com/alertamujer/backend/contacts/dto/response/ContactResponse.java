package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Own relationship, its safe counterpart presentation and Backend-authorized actions. */
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
