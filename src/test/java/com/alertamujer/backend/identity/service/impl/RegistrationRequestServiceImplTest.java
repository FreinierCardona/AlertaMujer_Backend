package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.RegistrationRequestEntity;
import com.alertamujer.backend.identity.repository.RegistrationRequestRepository;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RegistrationRequestServiceImplTest {

    private RegistrationRequestRepository repository;
    private BCryptPasswordEncoder passwordEncoder;
    private RegistrationRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(RegistrationRequestRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        SystemConfigurationValues configuration = new SystemConfigurationValues(
                "I need help", (short) 30, (short) 60, (short) 10, 1_000_000,
                (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
        service = new RegistrationRequestServiceImpl(repository, passwordEncoder, configuration,
                Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsOnlyASelfRegisteredPendingRequestWithServerTermsTimestamp() {
        when(repository.existsIdentityConflict(anyString(), anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(RegistrationRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequestResponse response = service.startPublicRegistration(publicInput());

        ArgumentCaptor<RegistrationRequestEntity> requestCaptor = ArgumentCaptor.forClass(RegistrationRequestEntity.class);
        verify(repository).saveAndFlush(requestCaptor.capture());
        RegistrationRequestEntity request = requestCaptor.getValue();
        assertThat(response.registrationRequestId()).isEqualTo(request.getRegistrationRequestId());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(request.getAccountOrigin()).isEqualTo(AccountOrigin.SELF_REGISTERED);
        assertThat(request.getAcceptedTermsAt()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));
        assertThat(request.getExpiresAt()).isEqualTo(Instant.parse("2026-10-06T21:00:00Z"));
        assertThat(request.getEmail()).isEqualTo("ana@example.com");
        assertThat(passwordEncoder.matches("SecurePass#2026", request.getPasswordHash())).isTrue();
        assertThat(request.getPasswordHash()).doesNotContain("SecurePass#2026");
    }

    @Test
    void createsAdministrativeRequestWithoutTermsOrClientSelectedRole() {
        when(repository.existsIdentityConflict(anyString(), anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(RegistrationRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.startAdministrativeRegistration(new AdminRegistrationRequestInput(
                "@admin_user", "Ana", "Perez", "ANA@EXAMPLE.COM", "3001234567", "SecurePass#2026"));

        ArgumentCaptor<RegistrationRequestEntity> requestCaptor = ArgumentCaptor.forClass(RegistrationRequestEntity.class);
        verify(repository).saveAndFlush(requestCaptor.capture());
        RegistrationRequestEntity request = requestCaptor.getValue();
        assertThat(request.getAccountOrigin()).isEqualTo(AccountOrigin.ADMIN_CREATED);
        assertThat(request.getAcceptedTermsAt()).isNull();
        assertThat(request.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void rejectsExistingIdentityWithTheSameSafeBusinessError() {
        when(repository.existsIdentityConflict(anyString(), anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.startPublicRegistration(publicInput()))
                .isInstanceOf(RuleViolationException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentUniqueIndexViolationWithoutLeakingTheIdentity() {
        when(repository.existsIdentityConflict(anyString(), anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(RegistrationRequestEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique index"));

        assertThatThrownBy(() -> service.startPublicRegistration(publicInput()))
                .isInstanceOf(RuleViolationException.class);
    }

    private RegistrationRequestInput publicInput() {
        return new RegistrationRequestInput(
                "@ana_user", " Ana ", " Perez ", "ANA@EXAMPLE.COM", "3001234567", "SecurePass#2026", true);
    }
}
