package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.alertamujer.backend.identity.dto.request.ContactChangeVerifyInput;
import com.alertamujer.backend.evidence.storage.EvidenceFileCleanup;
import com.alertamujer.backend.identity.dto.request.ProfileUpdateInput;
import com.alertamujer.backend.identity.dto.request.SosMessageInput;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.repository.ProfileRepository;
import com.alertamujer.backend.identity.repository.ProfileRepository.UserProfileData;
import com.alertamujer.backend.identity.repository.ProfileRepository.VerificationCodeData;
import com.alertamujer.backend.identity.service.OtpEmailSender;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProfileServiceImplTest {
    private final Instant now = Instant.parse("2026-10-07T05:00:00Z");
    private ProfileRepository repository;
    private PasswordEncoder passwordEncoder;
    private ProfileServiceImpl service;
    private UserProfileData user;
    private AuthenticatedIdentity identity;

    @BeforeEach
    void setUp() {
        repository = mock(ProfileRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        user = new UserProfileData(UUID.randomUUID(), "@ana", "Ana", "Perez", "ana@example.com", "3000000000",
                "USER", "ENABLED", AccountOrigin.SELF_REGISTERED, now.minusSeconds(1));
        identity = new AuthenticatedIdentity(user.id(), UUID.randomUUID(), "USER", false);
        SystemConfigurationValues config = new SystemConfigurationValues("Necesito ayuda", (short) 60, (short) 120,
                (short) 10, 1_048_576, (short) 500, (short) 5, (short) 5, (short) 3, (short) 300);
        service = new ProfileServiceImpl(repository, passwordEncoder, mock(OtpEmailSender.class), config,
                mock(EvidenceFileCleanup.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void profileUpdateUsesOnlyTheAuthenticatedOwnerAndReturnsSafeFields() {
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));

        var response = service.updateProfile(identity, new ProfileUpdateInput("@ana_nueva", "Ana Maria", null));

        verify(repository).updateProfile(user.id(), "@ana_nueva", "Ana Maria", "Perez", now);
        assertThat(response.userId()).isEqualTo(user.id());
        assertThat(response.username()).isEqualTo("@ana_nueva");
        assertThat(response.getClass().getRecordComponents()).extracting(component -> component.getName())
                .doesNotContain("password", "passwordHash", "otp", "refreshToken");
    }

    @Test
    void emailContactChangeConsumesMatchingOtpAndRevokesEverySession() {
        VerificationCodeData code = new VerificationCodeData(UUID.randomUUID(), now.plusSeconds(60), null, null, 0, 5, 0,
                now.minusSeconds(10));
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        when(repository.lockLatestContactChangeCode(user.id(), com.alertamujer.backend.identity.model.OtpChannel.EMAIL,
                "nuevo@example.com")).thenReturn(Optional.of(code));
        when(repository.readVerificationCodeHash(code.id())).thenReturn("hash");
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        service.verifyContactChange(identity, new ContactChangeVerifyInput("email", " Nuevo@Example.Com ", "123456"));

        verify(repository).updateContact(user.id(), "email", "nuevo@example.com", now);
        verify(repository).markCodeUsed(code.id(), now);
        verify(repository).revokeAllSessions(user.id(), now);
    }

    @Test
    void invalidOrMismatchedContactOtpDoesNotUpdateTheAccount() {
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        when(repository.lockLatestContactChangeCode(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyContactChange(identity,
                new ContactChangeVerifyInput("phone", "3000000001", "123456"))).isInstanceOf(RuleViolationException.class);
        verify(repository, never()).updateContact(any(), any(), any(), any());
    }

    @Test
    void termsAreOnlyAcceptedByAnAdminCreatedAccountAndAreNotDuplicated() {
        UserProfileData adminCreated = new UserProfileData(user.id(), user.username(), user.firstNames(), user.lastNames(),
                user.email(), user.phone(), user.role(), user.accountStatus(), AccountOrigin.ADMIN_CREATED, null);
        when(repository.lockUser(user.id())).thenReturn(Optional.of(adminCreated));
        service.acceptTerms(identity);
        verify(repository).acceptTerms(user.id(), now);

        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.acceptTerms(identity)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void blankSosMessageIsRejectedAndMissingPreferenceUsesConfiguredDefault() {
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.saveEmergencySettings(identity, new SosMessageInput("   ")))
                .isInstanceOf(RuleViolationException.class);
        when(repository.findUser(user.id())).thenReturn(Optional.of(user));
        when(repository.findEmergencyMessage(user.id())).thenReturn(Optional.empty());
        assertThat(service.getEmergencySettings(identity).message()).isEqualTo("Necesito ayuda");
    }

    @Test
    void accountDeletionRejectsAnOpenEmergencyAndDeletesOnlyTheOwnRootOtherwise() {
        when(repository.lockUser(user.id())).thenReturn(Optional.of(user));
        when(repository.hasOpenEmergency(user.id())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteAccount(identity)).isInstanceOf(StateConflictException.class);
        verify(repository, never()).deleteUser(any());

        when(repository.hasOpenEmergency(user.id())).thenReturn(false);
        service.deleteAccount(identity);
        verify(repository).deleteUser(user.id());
    }
}
