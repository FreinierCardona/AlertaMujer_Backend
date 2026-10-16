package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.dto.request.PhoneFirstRegistrationInput;
import com.alertamujer.backend.identity.dto.request.RegistrationCredentialsInput;
import com.alertamujer.backend.identity.dto.response.PhoneFirstRegistrationResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationStageResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.model.OtpPurpose;
import com.alertamujer.backend.identity.model.RegistrationStage;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.RegistrationRequestData;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.VerificationCodeData;
import com.alertamujer.backend.identity.service.OtpEmailSender;
import com.alertamujer.backend.identity.service.PhoneFirstRegistrationService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned public registration flow. The phone confirmation is explicitly a
 * controlled simulator: it neither sends nor reads an SMS.
 */
@Service
class PhoneFirstRegistrationServiceImpl implements PhoneFirstRegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityOtpRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailSender emailSender;
    private final SystemConfigurationValues configuration;
    private final Clock clock;
    private final boolean phoneSimulatorEnabled;

    @Autowired
    PhoneFirstRegistrationServiceImpl(
            IdentityOtpRepository repository,
            PasswordEncoder passwordEncoder,
            OtpEmailSender emailSender,
            SystemConfigurationValues configuration,
            @Value("${identity.phone-simulator.enabled:false}") boolean phoneSimulatorEnabled) {
        this(repository, passwordEncoder, emailSender, configuration, Clock.systemUTC(), phoneSimulatorEnabled);
    }

    PhoneFirstRegistrationServiceImpl(
            IdentityOtpRepository repository,
            PasswordEncoder passwordEncoder,
            OtpEmailSender emailSender,
            SystemConfigurationValues configuration,
            Clock clock,
            boolean phoneSimulatorEnabled) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.configuration = configuration;
        this.clock = clock;
        this.phoneSimulatorEnabled = phoneSimulatorEnabled;
    }

    @Override
    @Transactional
    public PhoneFirstRegistrationResponse start(PhoneFirstRegistrationInput input) {
        requirePhoneSimulator();
        String username = input.username().trim();
        String phone = input.phone().trim();
        if (repository.existsPhoneFirstIdentityConflict(username, phone)) {
            throw new RuleViolationException();
        }

        Instant now = clock.instant();
        Instant requestExpiry = now.plusSeconds(configuration.otpTtlMinutes() * 60L);
        UUID requestId = UUID.randomUUID();
        String proof = randomProof();
        try {
            repository.insertPhoneFirstRegistrationRequest(
                    requestId,
                    username,
                    input.firstNames().trim(),
                    input.lastNames().trim(),
                    phone,
                    passwordEncoder.encode(proof),
                    requestExpiry,
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw new RuleViolationException();
        }

        RegistrationStageResponse issued = issuePhoneSimulatorCode(requestId, phone, now);
        return new PhoneFirstRegistrationResponse(
                requestId,
                RegistrationStage.PHONE_PENDING.name(),
                proof,
                issued.expiresAt(),
                issued.simulatedPhoneCode());
    }

    @Override
    @Transactional
    public RegistrationStageResponse issueCode(UUID registrationRequestId, String registrationProof, OtpChannel channel) {
        RegistrationRequestData request = lockV2(registrationRequestId, registrationProof);
        Instant now = clock.instant();
        requirePending(request, now);
        if (channel == OtpChannel.PHONE_SIMULATOR && stageIs(request, RegistrationStage.PHONE_PENDING)) {
            return issuePhoneSimulatorCode(request.id(), request.phone(), now);
        }
        if (channel == OtpChannel.EMAIL && stageIs(request, RegistrationStage.EMAIL_PENDING)) {
            return issueEmailCode(request.id(), request.email(), now);
        }
        throw new RuleViolationException();
    }

    @Override
    @Transactional
    public RegistrationStageResponse completeCredentials(
            UUID registrationRequestId,
            String registrationProof,
            RegistrationCredentialsInput input) {
        RegistrationRequestData request = lockV2(registrationRequestId, registrationProof);
        Instant now = clock.instant();
        requirePending(request, now);
        requireStage(request, RegistrationStage.PHONE_VERIFIED);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsEmailConflictExceptRequest(request.id(), email)) {
            throw new RuleViolationException();
        }
        try {
            repository.setV2Credentials(request.id(), email, passwordEncoder.encode(input.password()), now);
        } catch (DataIntegrityViolationException exception) {
            throw new RuleViolationException();
        }
        return issueEmailCode(request.id(), email, now);
    }

    @Override
    @Transactional
    public RegistrationVerificationResponse verifyCode(
            UUID registrationRequestId,
            String registrationProof,
            OtpChannel channel,
            String code) {
        RegistrationRequestData request = lockV2(registrationRequestId, registrationProof);
        Instant now = clock.instant();
        requirePending(request, now);
        if (channel == OtpChannel.PHONE_SIMULATOR) {
            requireStage(request, RegistrationStage.PHONE_PENDING);
            VerificationCodeData otp = usableCode(request, OtpPurpose.PHONE_VERIFICATION, channel, request.phone(), now);
            consume(otp, code, now);
            repository.markPhoneVerified(request.id(), now);
            return new RegistrationVerificationResponse(RegistrationStage.PHONE_VERIFIED.name(), null);
        }
        if (channel == OtpChannel.EMAIL) {
            requireStage(request, RegistrationStage.EMAIL_PENDING);
            VerificationCodeData otp = usableCode(request, OtpPurpose.EMAIL_VERIFICATION, channel, request.email(), now);
            consume(otp, code, now);
            try {
                UUID userId = repository.createUserAndCredential(
                        request, repository.readRegistrationPasswordHash(request.id()), now);
                repository.markRegistrationCompleted(request.id(), now);
                return new RegistrationVerificationResponse("COMPLETED", userId);
            } catch (DataIntegrityViolationException exception) {
                throw new RuleViolationException();
            }
        }
        throw new RuleViolationException();
    }

    private RegistrationRequestData lockV2(UUID requestId, String proof) {
        RegistrationRequestData request = repository.lockRegistrationRequest(requestId)
                .orElseThrow(RuleViolationException::new);
        if (!"V2".equals(request.flowVersion()) || proof == null || proof.isBlank()
                || !passwordEncoder.matches(proof, repository.readRegistrationFlowProofHash(request.id()))) {
            throw new RuleViolationException();
        }
        return request;
    }

    private RegistrationStageResponse issuePhoneSimulatorCode(UUID requestId, String phone, Instant now) {
        requirePhoneSimulator();
        return issueCode(requestId, OtpPurpose.PHONE_VERIFICATION, OtpChannel.PHONE_SIMULATOR, phone, now,
                RegistrationStage.PHONE_PENDING);
    }

    private RegistrationStageResponse issueEmailCode(UUID requestId, String email, Instant now) {
        return issueCode(requestId, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL, email, now,
                RegistrationStage.EMAIL_PENDING);
    }

    private RegistrationStageResponse issueCode(
            UUID requestId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            Instant now,
            RegistrationStage stage) {
        int resendNumber = nextResendNumber(
                repository.lockLatestRegistrationCode(requestId, purpose, channel, destination), now);
        repository.invalidateActiveRegistrationCodes(requestId, purpose, channel, destination, now);
        String code = generateCode();
        Instant expiresAt = now.plusSeconds(configuration.otpTtlMinutes() * 60L);
        repository.insertRegistrationCode(
                UUID.randomUUID(), requestId, channel, purpose, destination, passwordEncoder.encode(code), expiresAt,
                configuration.otpMaxAttempts(), resendNumber, now);
        if (channel == OtpChannel.EMAIL) {
            emailSender.sendVerificationCode(destination, code, expiresAt);
        }
        return new RegistrationStageResponse(stage.name(), expiresAt,
                channel == OtpChannel.PHONE_SIMULATOR ? code : null);
    }

    private VerificationCodeData usableCode(
            RegistrationRequestData request,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            Instant now) {
        VerificationCodeData otp = repository.lockLatestRegistrationCode(request.id(), purpose, channel, destination)
                .orElseThrow(RuleViolationException::new);
        if (!otp.expiresAt().isAfter(now) || otp.usedAt() != null || otp.invalidatedAt() != null
                || otp.attemptCount() >= otp.maxAttempts()) {
            throw new RuleViolationException();
        }
        return otp;
    }

    private void consume(VerificationCodeData otp, String code, Instant now) {
        if (!passwordEncoder.matches(code, repository.readVerificationCodeHash(otp.id()))) {
            repository.recordFailedAttempt(otp.id(), now);
            throw new RuleViolationException();
        }
        repository.markCodeUsed(otp.id(), now);
    }

    private int nextResendNumber(java.util.Optional<VerificationCodeData> previous, Instant now) {
        if (previous.isEmpty()) return 0;
        VerificationCodeData latest = previous.get();
        if (latest.resendNumber() < configuration.otpMaxResends()) return latest.resendNumber() + 1;
        if (now.isBefore(latest.createdAt().plusSeconds(configuration.otpResendCooldownMinutes() * 60L))) {
            throw new RuleViolationException();
        }
        return 0;
    }

    private void requirePending(RegistrationRequestData request, Instant now) {
        if (!"PENDING".equals(request.status())) throw new RuleViolationException();
        if (!request.expiresAt().isAfter(now)) {
            repository.markRegistrationExpired(request.id(), now);
            throw new RuleViolationException();
        }
    }

    private void requireStage(RegistrationRequestData request, RegistrationStage expected) {
        if (!stageIs(request, expected)) throw new RuleViolationException();
    }

    private boolean stageIs(RegistrationRequestData request, RegistrationStage stage) {
        return stage.name().equals(request.registrationStage());
    }

    private void requirePhoneSimulator() {
        if (!phoneSimulatorEnabled) throw new RuleViolationException();
    }

    private String randomProof() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }
}
