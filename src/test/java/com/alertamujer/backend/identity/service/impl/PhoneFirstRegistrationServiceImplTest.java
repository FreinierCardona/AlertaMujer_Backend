package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.identity.dto.request.PhoneFirstRegistrationInput;
import com.alertamujer.backend.identity.dto.response.PhoneFirstRegistrationResponse;
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

class PhoneFirstRegistrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-16T18:00:00Z");

    private IdentityOtpRepository repository;
    private OtpEmailSender emailSender;
    private BCryptPasswordEncoder passwordEncoder;
    private SystemConfigurationValues configuration;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityOtpRepository.class);
        emailSender = mock(OtpEmailSender.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        configuration = new SystemConfigurationValues(
                "I need help", (short) 30, (short) 60, (short) 10, 1_000_000,
                (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
    }

    @Test
    void startsPhoneFirstFlowWithOnlyAHashedProofAndAnEphemeralSimulatorCode() {
        PhoneFirstRegistrationServiceImpl service = service(true);
        when(repository.existsPhoneFirstIdentityConflict("@ana_user", "3001234567")).thenReturn(false);
        when(repository.lockLatestRegistrationCode(any(), any(), any(), anyString())).thenReturn(Optional.empty());

        PhoneFirstRegistrationResponse response = service.start(
                new PhoneFirstRegistrationInput("@ana_user", "Ana", "Perez", "3001234567"));

        assertThat(response.stage()).isEqualTo("PHONE_PENDING");
        assertThat(response.registrationProof()).isNotBlank();
        assertThat(response.simulatedPhoneCode()).matches("^[0-9]{6}$");
        ArgumentCaptor<String> proofHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertPhoneFirstRegistrationRequest(any(), eq("@ana_user"), eq("Ana"), eq("Perez"),
                eq("3001234567"), proofHash.capture(), eq(NOW.plusSeconds(10_800)), eq(NOW));
        assertThat(proofHash.getValue()).doesNotContain(response.registrationProof());
        assertThat(passwordEncoder.matches(response.registrationProof(), proofHash.getValue())).isTrue();
        ArgumentCaptor<String> codeHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertRegistrationCode(any(), eq(response.registrationRequestId()),
                eq(OtpChannel.PHONE_SIMULATOR), eq(OtpPurpose.PHONE_VERIFICATION), eq("3001234567"),
                codeHash.capture(), eq(NOW.plusSeconds(10_800)), eq((short) 5), eq(0), eq(NOW));
        assertThat(passwordEncoder.matches(response.simulatedPhoneCode(), codeHash.getValue())).isTrue();
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void rejectsThePhoneSimulatorOutsideItsExplicitlyEnabledEnvironment() {
        PhoneFirstRegistrationServiceImpl service = service(false);

        assertThatThrownBy(() -> service.start(
                new PhoneFirstRegistrationInput("@ana_user", "Ana", "Perez", "3001234567")))
                .isInstanceOf(RuleViolationException.class);

        verify(repository, never()).insertPhoneFirstRegistrationRequest(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());
    }

    @Test
    void consumesTheSimulatorCodeOnlyWhenTheOpaqueProofAndPhoneStageMatch() {
        PhoneFirstRegistrationServiceImpl service = service(true);
        UUID requestId = UUID.randomUUID();
        RegistrationRequestData request = new RegistrationRequestData(
                requestId, "@ana_user", "Ana", "Perez", null, "3001234567", "PENDING",
                NOW.plusSeconds(600), AccountOrigin.SELF_REGISTERED, null, "V2", "PHONE_PENDING");
        VerificationCodeData code = new VerificationCodeData(UUID.randomUUID(), OtpChannel.PHONE_SIMULATOR,
                OtpPurpose.PHONE_VERIFICATION, "3001234567", NOW.plusSeconds(600), null, null, 0, 5, 0,
                NOW.minusSeconds(30));
        when(repository.lockRegistrationRequest(requestId)).thenReturn(Optional.of(request));
        when(repository.readRegistrationFlowProofHash(requestId)).thenReturn(passwordEncoder.encode("opaque-proof"));
        when(repository.lockLatestRegistrationCode(requestId, OtpPurpose.PHONE_VERIFICATION,
                OtpChannel.PHONE_SIMULATOR, "3001234567")).thenReturn(Optional.of(code));
        when(repository.readVerificationCodeHash(code.id())).thenReturn(passwordEncoder.encode("123456"));

        RegistrationVerificationResponse response = service.verifyCode(
                requestId, "opaque-proof", OtpChannel.PHONE_SIMULATOR, "123456");

        assertThat(response.status()).isEqualTo("PHONE_VERIFIED");
        verify(repository).markCodeUsed(code.id(), NOW);
        verify(repository).markPhoneVerified(requestId, NOW);
    }

    private PhoneFirstRegistrationServiceImpl service(boolean phoneSimulatorEnabled) {
        return new PhoneFirstRegistrationServiceImpl(repository, passwordEncoder, emailSender, configuration,
                Clock.fixed(NOW, ZoneOffset.UTC), phoneSimulatorEnabled);
    }
}
