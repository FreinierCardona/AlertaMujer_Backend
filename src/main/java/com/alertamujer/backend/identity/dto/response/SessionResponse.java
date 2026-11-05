package com.alertamujer.backend.identity.dto.response;

/** Los tokens se emiten solo al iniciar o renovar sesión; no se expone ningún secreto de persistencia. */
public record SessionResponse(
        String accessToken,
        String refreshToken,
        AuthenticatedUserResponse user,
        boolean termsPending) {
}
