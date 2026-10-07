package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Safe account representation returned with a freshly created session. */
public record AuthenticatedUserResponse(
        UUID userId,
        String username,
        String firstNames,
        String lastNames,
        String email,
        String phone,
        String role,
        String accountStatus) {
}
