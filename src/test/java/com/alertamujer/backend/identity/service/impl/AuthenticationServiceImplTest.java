package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.alertamujer.backend.identity.dto.request.LoginInput;
import com.alertamujer.backend.identity.dto.request.PasswordChangeInput;
import com.alertamujer.backend.identity.dto.request.PasswordResetConfirmInput;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.PasswordResetCode;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.SessionData;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.UserAccount;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.security.JwtAccessTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceImplTest {

    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");
    private IdentityAuthenticationRepository repository;
    private PasswordEncoder passwordEncoder;
    private JwtAccessTokenService jwt;
    private AuthenticationServiceImpl service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityAuthenticationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwt = mock(JwtAccessTokenService.class);
        service = new AuthenticationServiceImpl(repository, passwordEncoder, jwt, Clock.fixed(now, ZoneOffset.UTC));
        user = new UserAccount(UUID.randomUUID(), "@ana", "Ana", "Perez", "ana@example.com", "3000000000",
                "USER", "ENABLED", AccountOrigin.SELF_REGISTERED, now.minusSeconds(1));
    }

    @Test
    void loginByEmailCreatesMobileSessionWithAHashedRefreshAndMinimalJwtClaims() {
        when(repository.findUserByIdentifier("ana@example.com")).thenReturn(Optional.of(user));
        when(repository.readPasswordHash(user.id())).thenReturn("stored-password-hash");
        when(passwordEncoder.matches("SecurePass#2026", "stored-password-hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("stored-refresh-hash");
        when(jwt.issue(any(), eq(now), eq(now.plusSeconds(900)))).thenReturn("access-token");

        var response = service.login(new LoginInput("Ana@Example.Com", "SecurePass#2026"));

        ArgumentCaptor<UUID> sessionId = ArgumentCaptor.forClass(UUID.class);
        verify(repository).createSession(sessionId.capture(), eq(user.id()), eq("stored-refresh-hash"), eq("MOBILE"),
                eq(now.plusSeconds(60L * 24 * 60 * 60)), eq(now));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).startsWith(sessionId.getValue().toString() + ".");
        assertThat(response.termsPending()).isFalse();
        assertThat(response.user().email()).isEqualTo("ana@example.com");
        verify(repository).markLogin(user.id(), now);
    }

    @Test
    void loginReturnsTheSameUnauthorizedFailureForUnknownDisabledAndWrongCredentials() {
        when(repository.findUserByIdentifier("missing")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginInput("missing", "anything")))
                .isInstanceOf(UnauthorizedException.class);

        when(repository.findUserByIdentifier("ana@example.com"))
                .thenReturn(Optional.of(new UserAccount(user.id(), user.username(), user.firstNames(), user.lastNames(),
                        user.email(), user.phone(), user.role(), "DISABLED", user.accountOrigin(), user.acceptedTermsAt())));
        when(repository.readPasswordHash(user.id())).thenReturn("stored-password-hash");
        assertThatThrownBy(() -> service.login(new LoginInput("ana@example.com", "anything")))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).createSession(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void refreshRotatesTheOpaqueTokenAndRejectsARevokedSession() {
        UUID sessionId = UUID.randomUUID();
        String refresh = sessionId + ".secret";
        when(repository.lockSession(sessionId)).thenReturn(Optional.of(new SessionData(
                sessionId, user.id(), "MOBILE", now.minusSeconds(10), now.plusSeconds(20), null)));
        when(repository.readRefreshTokenHash(sessionId)).thenReturn("stored-refresh-hash");
        when(passwordEncoder.matches(refresh, "stored-refresh-hash")).thenReturn(true);
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("rotated-hash");
        when(jwt.issue(any(), eq(now), eq(now.plusSeconds(900)))).thenReturn("rotated-access");

        var response = service.refresh(refresh);

        verify(repository).rotateSession(eq(sessionId), eq("rotated-hash"),
                eq(now.plusSeconds(60L * 24 * 60 * 60)), eq(now));
        verify(repository).touchSessionAndUser(sessionId, user.id(), now);
        assertThat(response.refreshToken()).startsWith(sessionId.toString() + ".").isNotEqualTo(refresh);

        when(repository.lockSession(sessionId)).thenReturn(Optional.of(new SessionData(
                sessionId, user.id(), "MOBILE", now.minusSeconds(10), now.plusSeconds(20), now.minusSeconds(1))));
        assertThatThrownBy(() -> service.refresh(refresh)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void passwordResetConsumesOtpChangesHashAndRevokesAllSessionsAtomically() {
        UUID otpId = UUID.randomUUID();
        when(repository.lockUserByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(repository.lockLatestPasswordResetCode(user.id(), "ana@example.com"))
                .thenReturn(Optional.of(new PasswordResetCode(otpId, now.plusSeconds(60), null, null, 0, 5)));
        when(repository.readVerificationCodeHash(otpId)).thenReturn("otp-hash");
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewSecure#2026")).thenReturn("new-password-hash");

        service.confirmPasswordReset(new PasswordResetConfirmInput(" Ana@Example.Com ", "123456", "NewSecure#2026"));

        verify(repository).markVerificationCodeUsed(otpId, now);
        verify(repository).updatePassword(user.id(), "new-password-hash", now);
        verify(repository).revokeAllSessions(user.id(), now);
    }

    @Test
    void currentPasswordChangeRejectsReuseAndRevokesEverySession() {
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        when(repository.readPasswordHash(user.id())).thenReturn("old-hash");
        when(passwordEncoder.matches("OldSecure#2026", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("OldSecure#2026", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new AuthenticatedIdentity(user.id(), UUID.randomUUID(), "USER", false),
                new PasswordChangeInput("OldSecure#2026", "OldSecure#2026")))
                .isInstanceOf(RuleViolationException.class);

        when(passwordEncoder.matches("NewSecure#2026", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewSecure#2026")).thenReturn("new-hash");
        service.changePassword(new AuthenticatedIdentity(user.id(), UUID.randomUUID(), "USER", false),
                new PasswordChangeInput("OldSecure#2026", "NewSecure#2026"));
        verify(repository).revokeAllSessions(user.id(), now);
    }

    @Test
    void aStillSignedAccessTokenCanRepeatLogoutAfterItsSessionWasRevoked() {
        UUID sessionId = UUID.randomUUID();
        when(repository.lockSession(sessionId)).thenReturn(Optional.of(new SessionData(
                sessionId, user.id(), "MOBILE", now.minusSeconds(10), now.plusSeconds(60), now.minusSeconds(1))));
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));

        AuthenticatedIdentity identity = service.validateLogoutSession(user.id(), sessionId, "USER");
        service.logout(identity);

        assertThat(identity.sessionId()).isEqualTo(sessionId);
        verify(repository).revokeSession(sessionId, now);
    }
}
