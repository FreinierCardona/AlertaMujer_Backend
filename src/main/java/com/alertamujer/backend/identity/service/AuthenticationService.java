package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.request.LoginInput;
import com.alertamujer.backend.identity.dto.request.PasswordChangeInput;
import com.alertamujer.backend.identity.dto.request.PasswordResetConfirmInput;
import com.alertamujer.backend.identity.dto.response.SessionResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.util.UUID;

/** Session and password operations defined by HU-API-009. */
public interface AuthenticationService {

    SessionResponse login(LoginInput input);

    SessionResponse refresh(String refreshToken);

    void logout(AuthenticatedIdentity identity);

    void confirmPasswordReset(PasswordResetConfirmInput input);

    void changePassword(AuthenticatedIdentity identity, PasswordChangeInput input);

    AuthenticatedIdentity validateAccessSession(UUID userId, UUID sessionId, String role);

    AuthenticatedIdentity validateLogoutSession(UUID userId, UUID sessionId, String role);
}
