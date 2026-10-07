package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.model.OtpPurpose;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.RegistrationRequestData;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.UserData;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.VerificationCodeData;
import com.alertamujer.backend.identity.service.OtpEmailSender;
import com.alertamujer.backend.identity.service.OtpService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the OTP lifecycle documented for identity without adding state outside PostgreSQL. */
@Service
class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PURGE_BATCH_SIZE = 500;

    private final IdentityOtpRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailSender emailSender;
    private final SystemConfigurationValues configuration;
    private final Clock clock;

    @Autowired
    OtpServiceImpl(IdentityOtpRepository repository, PasswordEncoder passwordEncoder, OtpEmailSender emailSender,
            SystemConfigurationValues configuration) {
        this(repository, passwordEncoder, emailSender, configuration, Clock.systemUTC());
    }

    OtpServiceImpl(IdentityOtpRepository repository, PasswordEncoder passwordEncoder, OtpEmailSender emailSender,
            SystemConfigurationValues configuration, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OtpIssuedResponse issueRegistrationCode(UUID registrationRequestId, OtpChannel channel) {
        RegistrationRequestData request = repository.lockRegistrationRequest(registrationRequestId)
                .orElseThrow(ResourceNotFoundException::new);
        Instant now = clock.instant();
        requirePendingRequest(request, now);
        OtpPurpose purpose = purposeForRegistrationChannel(channel);
        return issueRegistrationCode(request, purpose, channel, requestDestination(request, channel), now);
    }

    @Override
    @Transactional
    public RegistrationVerificationResponse verifyRegistrationCode(UUID registrationRequestId, OtpChannel channel,
            String code) {
        RegistrationRequestData request = repository.lockRegistrationRequest(registrationRequestId)
                .orElseThrow(ResourceNotFoundException::new);
        Instant now = clock.instant();
        requirePendingRequest(request, now);

        OtpPurpose purpose = purposeForRegistrationChannel(channel);
        VerificationCodeData verificationCode = repository.lockLatestRegistrationCode(
                        request.id(), purpose, channel, requestDestination(request, channel))
                .orElseThrow(RuleViolationException::new);
        requireUsable(verificationCode, now);

        String hash = repository.readVerificationCodeHash(verificationCode.id());
        if (!passwordEncoder.matches(code, hash)) {
            repository.recordFailedAttempt(verificationCode.id(), now);
            throw new RuleViolationException();
        }
        repository.markCodeUsed(verificationCode.id(), now);

        boolean emailVerified = repository.isRegistrationChannelVerified(
                request.id(), OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL);
        boolean phoneVerified = repository.isRegistrationChannelVerified(
                request.id(), OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS);
        if (!emailVerified || !phoneVerified) {
            return new RegistrationVerificationResponse("PENDING", null);
        }

        try {
            UUID userId = repository.createUserAndCredential(
                    request, repository.readRegistrationPasswordHash(request.id()), now);
            repository.markRegistrationCompleted(request.id(), now);
            return new RegistrationVerificationResponse("COMPLETED", userId);
        } catch (DataIntegrityViolationException exception) {
            // Unique constraints are the final guard against a concurrent identity change.
            throw new RuleViolationException();
        }
    }

    @Override
    @Transactional
    public void requestPasswordResetCode(String email) {
        String destination = email.trim().toLowerCase(Locale.ROOT);
        UserData user = repository.lockUserByEmail(destination).orElse(null);
        if (user == null) {
            return; // The public contract must not disclose whether the account exists.
        }
        Instant now = clock.instant();
        issueUserCode(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, destination, now);
    }

    @Override
    @Transactional
    public int purgeTemporaryIdentityData() {
        Instant now = clock.instant();
        return repository.deleteInvalidVerificationCodes(now, PURGE_BATCH_SIZE)
                + repository.deleteStaleRegistrationRequests(now, PURGE_BATCH_SIZE);
    }

    private OtpIssuedResponse issueRegistrationCode(RegistrationRequestData request, OtpPurpose purpose,
            OtpChannel channel, String destination, Instant now) {
        int resendNumber = nextResendNumber(repository.lockLatestRegistrationCode(
                request.id(), purpose, channel, destination), now);
        repository.invalidateActiveRegistrationCodes(request.id(), purpose, channel, destination, now);
        String code = generateCode();
        Instant expiresAt = now.plusSeconds(configuration.otpTtlMinutes() * 60L);
        repository.insertRegistrationCode(UUID.randomUUID(), request.id(), channel, purpose, destination,
                passwordEncoder.encode(code), expiresAt, configuration.otpMaxAttempts(), resendNumber, now);
        deliver(channel, destination, code, expiresAt);
        return new OtpIssuedResponse(expiresAt, channel == OtpChannel.SMS ? code : null);
    }

    /** Reused by recovery now and by the authenticated contact-change HU later. */
    private void issueUserCode(UserData user, OtpPurpose purpose, OtpChannel channel, String destination, Instant now) {
        int resendNumber = nextResendNumber(repository.lockLatestUserCode(user.id(), purpose, channel, destination), now);
        repository.invalidateActiveUserCodes(user.id(), purpose, channel, destination, now);
        String code = generateCode();
        Instant expiresAt = now.plusSeconds(configuration.otpTtlMinutes() * 60L);
        repository.insertUserCode(UUID.randomUUID(), user.id(), channel, purpose, destination,
                passwordEncoder.encode(code), expiresAt, configuration.otpMaxAttempts(), resendNumber, now);
        deliver(channel, destination, code, expiresAt);
    }

    private int nextResendNumber(java.util.Optional<VerificationCodeData> previous, Instant now) {
        if (previous.isEmpty()) {
            return 0;
        }
        VerificationCodeData latest = previous.get();
        if (latest.resendNumber() < configuration.otpMaxResends()) {
            return latest.resendNumber() + 1;
        }
        if (now.isBefore(latest.createdAt().plusSeconds(configuration.otpResendCooldownMinutes() * 60L))) {
            throw new RuleViolationException();
        }
        return 0;
    }

    private void requirePendingRequest(RegistrationRequestData request, Instant now) {
        if (!"PENDING".equals(request.status())) {
            throw new RuleViolationException();
        }
        if (!request.expiresAt().isAfter(now)) {
            repository.markRegistrationExpired(request.id(), now);
            throw new RuleViolationException();
        }
    }

    private void requireUsable(VerificationCodeData code, Instant now) {
        if (!code.expiresAt().isAfter(now) || code.usedAt() != null || code.invalidatedAt() != null
                || code.attemptCount() >= code.maxAttempts()) {
            throw new RuleViolationException();
        }
    }

    private OtpPurpose purposeForRegistrationChannel(OtpChannel channel) {
        return switch (channel) {
            case EMAIL -> OtpPurpose.EMAIL_VERIFICATION;
            case SMS -> OtpPurpose.PHONE_VERIFICATION;
        };
    }

    private String requestDestination(RegistrationRequestData request, OtpChannel channel) {
        return channel == OtpChannel.EMAIL ? request.email() : request.phone();
    }

    private void deliver(OtpChannel channel, String destination, String code, Instant expiresAt) {
        if (channel == OtpChannel.EMAIL) {
            emailSender.sendVerificationCode(destination, code, expiresAt);
        }
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }
}
