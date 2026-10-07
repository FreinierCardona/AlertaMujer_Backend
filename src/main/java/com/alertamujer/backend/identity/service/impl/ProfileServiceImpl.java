package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.dto.request.ContactChangeRequestInput;
import com.alertamujer.backend.identity.dto.request.ContactChangeVerifyInput;
import com.alertamujer.backend.identity.dto.request.ProfileUpdateInput;
import com.alertamujer.backend.identity.dto.request.SosMessageInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.SosMessageResponse;
import com.alertamujer.backend.evidence.storage.EvidenceFileCleanup;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.repository.ProfileRepository;
import com.alertamujer.backend.identity.repository.ProfileRepository.UserProfileData;
import com.alertamujer.backend.identity.repository.ProfileRepository.VerificationCodeData;
import com.alertamujer.backend.identity.service.OtpEmailSender;
import com.alertamujer.backend.identity.service.ProfileService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owns only profile state; contact secrets stay in the existing restricted OTP storage. */
@Service
class ProfileServiceImpl implements ProfileService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ProfileRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailSender emailSender;
    private final SystemConfigurationValues configuration;
    private final EvidenceFileCleanup evidenceFileCleanup;
    private final Clock clock;

    @Autowired
    ProfileServiceImpl(ProfileRepository repository, PasswordEncoder passwordEncoder, OtpEmailSender emailSender,
            SystemConfigurationValues configuration, EvidenceFileCleanup evidenceFileCleanup) {
        this(repository, passwordEncoder, emailSender, configuration, evidenceFileCleanup, Clock.systemUTC());
    }
    ProfileServiceImpl(ProfileRepository repository, PasswordEncoder passwordEncoder, OtpEmailSender emailSender,
            SystemConfigurationValues configuration, EvidenceFileCleanup evidenceFileCleanup, Clock clock) {
        this.repository = repository; this.passwordEncoder = passwordEncoder; this.emailSender = emailSender;
        this.configuration = configuration; this.evidenceFileCleanup = evidenceFileCleanup; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public AuthenticatedUserResponse getProfile(AuthenticatedIdentity identity) { return response(requireUser(identity, false)); }

    @Override @Transactional
    public AuthenticatedUserResponse updateProfile(AuthenticatedIdentity identity, ProfileUpdateInput input) {
        if (!input.hasChanges()) throw new RuleViolationException();
        UserProfileData user = requireEnabledUser(identity);
        String username = input.username() == null ? user.username() : input.username().trim().toLowerCase(Locale.ROOT);
        String firstNames = input.firstNames() == null ? user.firstNames() : trimmed(input.firstNames());
        String lastNames = input.lastNames() == null ? user.lastNames() : trimmed(input.lastNames());
        if (firstNames.isBlank() || lastNames.isBlank()) throw new RuleViolationException();
        try { repository.updateProfile(user.id(), username, firstNames, lastNames, clock.instant()); }
        catch (DataIntegrityViolationException exception) { throw new RuleViolationException(); }
        return new AuthenticatedUserResponse(user.id(), username, firstNames, lastNames, user.email(), user.phone(),
                user.role(), user.accountStatus());
    }

    @Override @Transactional
    public OtpIssuedResponse requestContactChange(AuthenticatedIdentity identity, ContactChangeRequestInput input) {
        UserProfileData user = requireEnabledUser(identity);
        Contact contact = contact(input.field(), input.value());
        if (contact.value().equals("email".equals(contact.field()) ? user.email() : user.phone())
                || repository.anotherUserHasContact(contact.field(), contact.value(), user.id())) throw new RuleViolationException();
        Instant now = clock.instant();
        VerificationCodeData previous = repository.lockLatestContactChangeCode(user.id(), contact.channel(), contact.value()).orElse(null);
        int resend = nextResend(previous, now);
        repository.invalidateContactChangeCodes(user.id(), contact.channel(), contact.value(), now);
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plusSeconds(configuration.otpTtlMinutes() * 60L);
        repository.insertContactChangeCode(UUID.randomUUID(), user.id(), contact.channel(), contact.value(),
                passwordEncoder.encode(code), expiresAt, configuration.otpMaxAttempts(), resend, now);
        if (contact.channel() == OtpChannel.EMAIL) emailSender.sendVerificationCode(contact.value(), code, expiresAt);
        return new OtpIssuedResponse(expiresAt, contact.channel() == OtpChannel.SMS ? code : null);
    }

    @Override @Transactional
    public void verifyContactChange(AuthenticatedIdentity identity, ContactChangeVerifyInput input) {
        UserProfileData user = requireEnabledUser(identity);
        Contact contact = contact(input.field(), input.value());
        VerificationCodeData code = repository.lockLatestContactChangeCode(user.id(), contact.channel(), contact.value())
                .orElseThrow(RuleViolationException::new);
        Instant now = clock.instant();
        if (!usable(code, now)) throw new RuleViolationException();
        if (!passwordEncoder.matches(input.code(), repository.readVerificationCodeHash(code.id()))) {
            repository.recordFailedAttempt(code.id(), now); throw new RuleViolationException();
        }
        if (repository.anotherUserHasContact(contact.field(), contact.value(), user.id())) throw new RuleViolationException();
        try { repository.updateContact(user.id(), contact.field(), contact.value(), now); }
        catch (DataIntegrityViolationException exception) { throw new RuleViolationException(); }
        repository.markCodeUsed(code.id(), now);
        if ("email".equals(contact.field())) repository.revokeAllSessions(user.id(), now);
    }

    @Override @Transactional
    public void acceptTerms(AuthenticatedIdentity identity) {
        UserProfileData user = requireUser(identity, true);
        if (user.accountOrigin() != AccountOrigin.ADMIN_CREATED) throw new RuleViolationException();
        if (user.acceptedTermsAt() == null) repository.acceptTerms(user.id(), clock.instant());
    }

    @Override @Transactional(readOnly = true)
    public SosMessageResponse getEmergencySettings(AuthenticatedIdentity identity) {
        UserProfileData user = requireUser(identity, false);
        return new SosMessageResponse(repository.findEmergencyMessage(user.id()).orElse(configuration.defaultSosMessage()));
    }

    @Override @Transactional
    public SosMessageResponse saveEmergencySettings(AuthenticatedIdentity identity, SosMessageInput input) {
        UserProfileData user = requireEnabledUser(identity);
        String message = trimmed(input.message());
        if (message.isBlank()) throw new RuleViolationException();
        repository.upsertEmergencyMessage(user.id(), message, clock.instant());
        return new SosMessageResponse(message);
    }

    @Override @Transactional
    public void deleteAccount(AuthenticatedIdentity identity) {
        UserProfileData user = requireUser(identity, true);
        if (repository.hasOpenEmergency(user.id())) throw new StateConflictException();
        List<String> references = repository.findEvidenceReferences(user.id());
        repository.deleteUser(user.id());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evidenceFileCleanup.deleteAfterAccountRemoval(references); }
            });
        } else {
            evidenceFileCleanup.deleteAfterAccountRemoval(references);
        }
    }

    private UserProfileData requireUser(AuthenticatedIdentity identity, boolean lock) {
        UserProfileData user = (lock ? repository.lockUser(identity.userId()) : repository.findUser(identity.userId()))
                .orElseThrow(UnauthorizedException::new);
        if (!user.role().equals(identity.role())) throw new UnauthorizedException();
        return user;
    }
    private UserProfileData requireEnabledUser(AuthenticatedIdentity identity) {
        UserProfileData user = requireUser(identity, true);
        if (!"ENABLED".equals(user.accountStatus())) throw new UnauthorizedException();
        return user;
    }
    private AuthenticatedUserResponse response(UserProfileData user) {
        return new AuthenticatedUserResponse(user.id(), user.username(), user.firstNames(), user.lastNames(), user.email(),
                user.phone(), user.role(), user.accountStatus());
    }
    private Contact contact(String field, String value) {
        String normalized = trimmed(value);
        if ("email".equals(field)) {
            normalized = normalized.toLowerCase(Locale.ROOT);
            if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new RuleViolationException();
            return new Contact(field, normalized, OtpChannel.EMAIL);
        }
        if ("phone".equals(field) && normalized.matches("^3[0-9]{9}$")) return new Contact(field, normalized, OtpChannel.SMS);
        throw new RuleViolationException();
    }
    private int nextResend(VerificationCodeData previous, Instant now) {
        if (previous == null) return 0;
        if (previous.resendNumber() < configuration.otpMaxResends()) return previous.resendNumber() + 1;
        if (now.isBefore(previous.createdAt().plusSeconds(configuration.otpResendCooldownMinutes() * 60L))) throw new RuleViolationException();
        return 0;
    }
    private boolean usable(VerificationCodeData code, Instant now) {
        return code.expiresAt().isAfter(now) && code.usedAt() == null && code.invalidatedAt() == null
                && code.attemptCount() < code.maxAttempts();
    }
    private String trimmed(String value) { return value == null ? "" : value.trim(); }
    private record Contact(String field, String value, OtpChannel channel) { }
}
