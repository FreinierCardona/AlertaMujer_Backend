package com.alertamujer.backend.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.notification.dto.request.DeviceTokenInput;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.TokenOwner;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationServiceImplTest {
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");
    private NotificationRepository repository;
    private NotificationServiceImpl service;
    private AuthenticatedIdentity identity;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationServiceImpl(repository, Clock.fixed(now, ZoneOffset.UTC));
        identity = new AuthenticatedIdentity(userId, UUID.randomUUID(), "USER", false);
        when(repository.isEnabledUser(userId)).thenReturn(true);
    }

    @Test
    void storesANewTokenWithoutReturningItsValue() {
        when(repository.findTokenOwner("device-token")).thenReturn(Optional.empty());

        assertThat(service.registerDeviceToken(identity, input("  device-token  "))).isTrue();

        verify(repository).insertToken(any(), eq(userId), eq("device-token"), eq("ANDROID"), eq(now));
    }

    @Test
    void reactivatesTheSameOwnersExistingToken() {
        UUID tokenId = UUID.randomUUID();
        when(repository.findTokenOwner("device-token")).thenReturn(Optional.of(new TokenOwner(tokenId, userId)));

        assertThat(service.registerDeviceToken(identity, input("device-token"))).isFalse();

        verify(repository).refreshToken(tokenId, "ANDROID", now);
    }

    @Test
    void hidesAnotherOwnersTokenBehindTheGenericConflict() {
        when(repository.findTokenOwner("device-token"))
                .thenReturn(Optional.of(new TokenOwner(UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> service.registerDeviceToken(identity, input("device-token")))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void resolvesAnInsertRaceOnlyForTheSameOwner() {
        UUID tokenId = UUID.randomUUID();
        when(repository.findTokenOwner("device-token"))
                .thenReturn(Optional.empty(), Optional.of(new TokenOwner(tokenId, userId)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unique"))
                .when(repository).insertToken(any(), any(), any(), any(), any());

        assertThat(service.registerDeviceToken(identity, input("device-token"))).isFalse();

        verify(repository).refreshToken(tokenId, "ANDROID", now);
    }

    private static DeviceTokenInput input(String token) {
        return new DeviceTokenInput(token, DeviceTokenInput.Platform.ANDROID);
    }
}
