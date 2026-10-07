package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.model.OtpPurpose;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.RegistrationRequestData;
import com.alertamujer.backend.identity.repository.IdentityOtpRepository.VerificationCodeData;
import com.alertamujer.backend.identity.service.OtpEmailSender;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class OtpServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-06T18:00:00Z");

    private IdentityOtpRepository repository;
    private OtpEmailSender emailSender;
    private BCryptPasswordEncoder passwordEncoder;
    private OtpServiceImpl service;
    private RegistrationRequestData request;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityOtpRepository.class);
        emailSender = mock(OtpEmailSender.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        SystemConfigurationValues configuration = new SystemConfigurationValues(
                "I need help", (short) 30, (short) 60, (short) 10, 1_000_000,
                (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
        service = new OtpServiceImpl(repository, passwordEncoder, emailSender, configuration,
                Clock.fixed(NOW, ZoneOffset.UTC));
        request = new RegistrationRequestData(UUID.randomUUID(), "@ana_user", "Ana", "Perez",
                "ana@example.com", "3001234567", "PENDING", NOW.plusSeconds(10_800),
                AccountOrigin.SELF_REGISTERED, NOW.minusSeconds(60));
    }

    @Test
    void returnsTheSixDigitCodeOnlyForTheAcademicSmsSimulationAndStoresItsHash() {
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.empty());

        OtpIssuedResponse response = service.issueRegistrationCode(request.id(), OtpChannel.SMS);

        assertThat(response.simulatedSmsCode()).matches("^[0-9]{6}$");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(10_800));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertRegistrationCode(any(), eq(request.id()), eq(OtpChannel.SMS),
                eq(OtpPurpose.PHONE_VERIFICATION), eq("3001234567"), hash.capture(),
                eq(NOW.plusSeconds(10_800)), eq((short) 5), eq(0), eq(NOW));
        assertThat(hash.getValue()).doesNotContain(response.simulatedSmsCode());
        assertThat(passwordEncoder.matches(response.simulatedSmsCode(), hash.getValue())).isTrue();
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendsEmailOtpByTheSmtpBoundaryWithoutReturningIt() {
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.empty());

        OtpIssuedResponse response = service.issueRegistrationCode(request.id(), OtpChannel.EMAIL);

        assertThat(response.simulatedSmsCode()).isNull();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq("ana@example.com"), code.capture(), eq(response.expiresAt()));
        assertThat(code.getValue()).matches("^[0-9]{6}$");
    }

    @Test
    void recordsTheFifthIncorrectAttemptAndRejectsTheOtp() {
        VerificationCodeData code = code(4, 0, null, null, NOW.plusSeconds(100));
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.of(code));
        when(repository.readVerificationCodeHash(code.id())).thenReturn(passwordEncoder.encode("123456"));

        assertThatThrownBy(() -> service.verifyRegistrationCode(request.id(), OtpChannel.EMAIL, "654321"))
                .isInstanceOf(RuleViolationException.class);

        verify(repository).recordFailedAttempt(code.id(), NOW);
        verify(repository, never()).markCodeUsed(any(), any());
    }

    @Test
    void consumesBothRegistrationChannelsAndCreatesTheAccountAtomicallyOnTheSecondOne() {
        VerificationCodeData code = code(0, 0, null, null, NOW.plusSeconds(100));
        UUID userId = UUID.randomUUID();
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.of(code));
        when(repository.readVerificationCodeHash(code.id())).thenReturn(passwordEncoder.encode("123456"));
        when(repository.isRegistrationChannelVerified(request.id(), OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL))
                .thenReturn(true);
        when(repository.isRegistrationChannelVerified(request.id(), OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS))
                .thenReturn(true);
        when(repository.readRegistrationPasswordHash(request.id())).thenReturn("registration-password-hash");
        when(repository.createUserAndCredential(request, "registration-password-hash", NOW)).thenReturn(userId);

        RegistrationVerificationResponse response = service.verifyRegistrationCode(
                request.id(), OtpChannel.EMAIL, "123456");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.userId()).isEqualTo(userId);
        verify(repository).markCodeUsed(code.id(), NOW);
        verify(repository).createUserAndCredential(request, "registration-password-hash", NOW);
        verify(repository).markRegistrationCompleted(request.id(), NOW);
    }

    @Test
    void rejectsAFourthResendUntilTheFiveHourCooldownHasPassed() {
        VerificationCodeData thirdResend = code(0, 3, null, NOW.minusSeconds(60), NOW.plusSeconds(100));
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.of(thirdResend));

        assertThatThrownBy(() -> service.issueRegistrationCode(request.id(), OtpChannel.SMS))
                .isInstanceOf(RuleViolationException.class);

        verify(repository, never()).insertRegistrationCode(any(), any(), any(), any(), anyString(), anyString(),
                any(), anyShort(), anyInt(), any());
    }

    @Test
    void rejectsAnAlreadyUsedOtpWithoutReadingItsHashAgain() {
        VerificationCodeData used = code(0, 0, NOW.minusSeconds(1), null, NOW.plusSeconds(100));
        when(repository.lockRegistrationRequest(request.id())).thenReturn(Optional.of(request));
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.verifyRegistrationCode(request.id(), OtpChannel.EMAIL, "123456"))
                .isInstanceOf(RuleViolationException.class);

        verify(repository, never()).readVerificationCodeHash(any());
    }

    @Test
    void keepsPasswordResetExistencePrivateAndPurgesOnlyBoundedBatches() {
        when(repository.lockUserByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(repository.deleteInvalidVerificationCodes(NOW, 500)).thenReturn(4);
        when(repository.deleteStaleRegistrationRequests(NOW, 500)).thenReturn(2);

        service.requestPasswordResetCode("MISSING@EXAMPLE.COM");

        assertThat(service.purgeTemporaryIdentityData()).isEqualTo(6);
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
        verify(repository).deleteInvalidVerificationCodes(NOW, 500);
        verify(repository).deleteStaleRegistrationRequests(NOW, 500);
    }

    private VerificationCodeData code(int attempts, int resends, Instant usedAt, Instant invalidatedAt, Instant expiresAt) {
        return new VerificationCodeData(UUID.randomUUID(), OtpChannel.EMAIL, OtpPurpose.EMAIL_VERIFICATION,
                "ana@example.com", expiresAt, usedAt, invalidatedAt, attempts, 5, resends, NOW.minusSeconds(30));
    }
}
