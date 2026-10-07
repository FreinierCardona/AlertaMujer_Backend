package com.alertamujer.backend.identity.dto.response;

/** Tokens are emitted only at login and refresh; no persistence secret is exposed. */
public record SessionResponse(
        String accessToken,
        String refreshToken,
        AuthenticatedUserResponse user,
        boolean termsPending) {
}
