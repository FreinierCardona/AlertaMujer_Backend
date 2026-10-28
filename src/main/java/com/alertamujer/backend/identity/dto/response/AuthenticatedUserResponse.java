package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Representación segura de la cuenta devuelta junto con una sesión recién creada. */
public record AuthenticatedUserResponse(
        UUID userId,
        String username,
        String firstNames,
        String lastNames,
        String email,
        String phone,
        String role,
        String accountStatus,
        boolean termsPending) {
    public AuthenticatedUserResponse(UUID userId, String username, String firstNames, String lastNames,
            String email, String phone, String role, String accountStatus) {
        this(userId, username, firstNames, lastNames, email, phone, role, accountStatus, false);
    }
}
