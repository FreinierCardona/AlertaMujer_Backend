package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.dto.request.LoginInput;
import com.alertamujer.backend.identity.dto.request.PasswordChangeInput;
import com.alertamujer.backend.identity.dto.request.PasswordResetConfirmInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.SessionResponse;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.PasswordResetCode;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.SessionData;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository.UserAccount;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.audit.AuditEvent;
import com.alertamujer.backend.shared.audit.AuditService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.security.JwtAccessTokenService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps authentication state in PostgreSQL so JWTs remain revocable. */
@Service
class AuthenticationServiceImpl implements AuthenticationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiLJhJe0KzE8Q7xYfTMx0mV5ykJ7XHi";
    private static final long MOBILE_SESSION_SECONDS = 60L * 24 * 60 * 60;
    private static final long ADMIN_SESSION_SECONDS = 10 * 60L;
    private static final long MOBILE_ACCESS_SECONDS = 15 * 60L;
    private static final long ADMIN_ACCESS_SECONDS = 5 * 60L;

    private final IdentityAuthenticationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtAccessTokenService jwt;
    private final Clock clock;
    private final AuditService auditService;

    @Autowired
    AuthenticationServiceImpl(IdentityAuthenticationRepository repository, PasswordEncoder passwordEncoder,
            JwtAccessTokenService jwt, AuditService auditService) {
        this(repository, passwordEncoder, jwt, Clock.systemUTC(), auditService);
    }

    AuthenticationServiceImpl(IdentityAuthenticationRepository repository, PasswordEncoder passwordEncoder,
            JwtAccessTokenService jwt, Clock clock) {
        this(repository, passwordEncoder, jwt, clock, AuditService.noop());
    }

    AuthenticationServiceImpl(IdentityAuthenticationRepository repository, PasswordEncoder passwordEncoder,
            JwtAccessTokenService jwt, Clock clock, AuditService auditService) {
        this.repository = repository; this.passwordEncoder = passwordEncoder; this.jwt = jwt;
        this.clock = clock; this.auditService = auditService;
    }

    @Override
    @Transactional
    public SessionResponse login(LoginInput input) {
        UserAccount user = repository.findUserByIdentifier(normalizeIdentifier(input.identifier())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(input.password(), DUMMY_PASSWORD_HASH);
            throw new UnauthorizedException();
        }
        String passwordHash = repository.readPasswordHash(user.id());
        if (!user.isEnabled() || !passwordEncoder.matches(input.password(), passwordHash)) {
            throw new UnauthorizedException();
        }
        Instant now = clock.instant();
        UUID sessionId = UUID.randomUUID();
        String refreshToken = refreshToken(sessionId);
        repository.createSession(sessionId, user.id(), passwordEncoder.encode(refreshToken), clientType(user),
                sessionExpiration(user, now), now);
        repository.markLogin(user.id(), now);
        if ("ENTITY_ADMIN".equals(user.role())) {
            auditService.record(AuditEvent.success(user.id(), user.id(), "ADMIN_LOGIN", "USER", user.id(),
                    null, null, "Administrative session started."));
        }
        return sessionResponse(user, sessionId, refreshToken, now);
    }

    @Override
    @Transactional
    public SessionResponse refresh(String refreshToken) {
        UUID sessionId = parseRefreshSessionId(refreshToken);
        SessionData session = repository.lockSession(sessionId).orElseThrow(UnauthorizedException::new);
        Instant now = clock.instant();
        if (!isActive(session, now) || !passwordEncoder.matches(refreshToken, repository.readRefreshTokenHash(sessionId))) {
            throw new UnauthorizedException();
        }
        UserAccount user = repository.lockUser(session.userId()).orElseThrow(UnauthorizedException::new);
        if (!user.isEnabled() || !clientType(user).equals(session.clientType())) {
            throw new UnauthorizedException();
        }
        String nextRefreshToken = refreshToken(sessionId);
        repository.rotateSession(sessionId, passwordEncoder.encode(nextRefreshToken), sessionExpiration(user, now), now);
        repository.touchSessionAndUser(sessionId, user.id(), now);
        return sessionResponse(user, sessionId, nextRefreshToken, now);
    }

    @Override
    @Transactional
    public void logout(AuthenticatedIdentity identity) {
        repository.revokeSession(identity.sessionId(), clock.instant());
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmInput input) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = repository.lockUserByEmail(email).orElseThrow(RuleViolationException::new);
        PasswordResetCode code = repository.lockLatestPasswordResetCode(user.id(), email)
                .orElseThrow(RuleViolationException::new);
        Instant now = clock.instant();
        if (!isUsable(code, now)) {
            throw new RuleViolationException();
        }
        if (!passwordEncoder.matches(input.code(), repository.readVerificationCodeHash(code.id()))) {
            repository.recordFailedVerificationAttempt(code.id(), now);
            throw new RuleViolationException();
        }
        repository.markVerificationCodeUsed(code.id(), now);
        repository.updatePassword(user.id(), passwordEncoder.encode(input.newPassword()), now);
        repository.revokeAllSessions(user.id(), now);
    }

    @Override
    @Transactional
    public void changePassword(AuthenticatedIdentity identity, PasswordChangeInput input) {
        UserAccount user = repository.lockUser(identity.userId()).orElseThrow(UnauthorizedException::new);
        if (!user.isEnabled()) {
            throw new UnauthorizedException();
        }
        String previousHash = repository.readPasswordHash(user.id());
        if (!passwordEncoder.matches(input.currentPassword(), previousHash)
                || passwordEncoder.matches(input.newPassword(), previousHash)) {
            throw new RuleViolationException();
        }
        Instant now = clock.instant();
        repository.updatePassword(user.id(), passwordEncoder.encode(input.newPassword()), now);
        repository.revokeAllSessions(user.id(), now);
    }

    @Override
    @Transactional
    public AuthenticatedIdentity validateAccessSession(UUID userId, UUID sessionId, String tokenRole) {
        SessionData session = repository.lockSession(sessionId).orElseThrow(UnauthorizedException::new);
        Instant now = clock.instant();
        if (!session.userId().equals(userId) || !isActive(session, now)) {
            throw new UnauthorizedException();
        }
        UserAccount user = repository.lockUser(userId).orElseThrow(UnauthorizedException::new);
        if (!user.isEnabled() || !user.role().equals(tokenRole) || !clientType(user).equals(session.clientType())) {
            throw new UnauthorizedException();
        }
        repository.touchSessionAndUser(sessionId, userId, now);
        return new AuthenticatedIdentity(userId, sessionId, user.role(), user.hasPendingTerms());
    }

    @Override
    @Transactional
    public AuthenticatedIdentity validateLogoutSession(UUID userId, UUID sessionId, String tokenRole) {
        SessionData session = repository.lockSession(sessionId).orElseThrow(UnauthorizedException::new);
        Instant now = clock.instant();
        if (!session.userId().equals(userId) || !session.expiresAt().isAfter(now)) {
            throw new UnauthorizedException();
        }
        UserAccount user = repository.lockUser(userId).orElseThrow(UnauthorizedException::new);
        if (!user.role().equals(tokenRole) || !clientType(user).equals(session.clientType())) {
            throw new UnauthorizedException();
        }
        return new AuthenticatedIdentity(userId, sessionId, user.role(), user.hasPendingTerms());
    }

    private SessionResponse sessionResponse(UserAccount user, UUID sessionId, String refreshToken, Instant now) {
        Instant accessExpiresAt = now.plusSeconds("ENTITY_ADMIN".equals(user.role())
                ? ADMIN_ACCESS_SECONDS : MOBILE_ACCESS_SECONDS);
        String accessToken = jwt.issue(new JwtAccessTokenService.UUIDClaims(user.id(), sessionId, user.role()),
                now, accessExpiresAt);
        return new SessionResponse(accessToken, refreshToken, toResponse(user), user.hasPendingTerms());
    }

    private AuthenticatedUserResponse toResponse(UserAccount user) {
        return new AuthenticatedUserResponse(user.id(), user.username(), user.firstNames(), user.lastNames(), user.email(),
                user.phone(), user.role(), user.accountStatus());
    }

    private String normalizeIdentifier(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String clientType(UserAccount user) {
        return "ENTITY_ADMIN".equals(user.role()) ? "ADMIN_WEB" : "MOBILE";
    }

    private Instant sessionExpiration(UserAccount user, Instant now) {
        return now.plusSeconds("ENTITY_ADMIN".equals(user.role()) ? ADMIN_SESSION_SECONDS : MOBILE_SESSION_SECONDS);
    }

    private boolean isActive(SessionData session, Instant now) {
        return session.revokedAt() == null && session.expiresAt().isAfter(now);
    }

    private boolean isUsable(PasswordResetCode code, Instant now) {
        return code.expiresAt().isAfter(now) && code.usedAt() == null && code.invalidatedAt() == null
                && code.attemptCount() < code.maxAttempts();
    }

    private String refreshToken(UUID sessionId) {
        // UUID prefix (37 characters including the separator) plus a 192-bit URL-safe value stays within BCrypt's
        // 72-byte input limit while retaining more than enough entropy for a refresh credential.
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private UUID parseRefreshSessionId(String refreshToken) {
        int separator = refreshToken.indexOf('.');
        if (separator < 1 || separator != refreshToken.lastIndexOf('.')) {
            throw new UnauthorizedException();
        }
        try {
            return UUID.fromString(refreshToken.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException();
        }
    }
}
