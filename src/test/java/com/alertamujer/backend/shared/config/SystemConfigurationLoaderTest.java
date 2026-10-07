package com.alertamujer.backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemConfigurationLoaderTest {

    private final SystemConfigurationRepository repository = mock(SystemConfigurationRepository.class);
    private final SystemConfigurationLoader loader = new SystemConfigurationLoader(repository);

    @Test
    void loadsTheApprovedSingletonAsImmutableTypedValues() {
        when(repository.findByConfigurationId((short) 1)).thenReturn(Optional.of(validConfiguration()));

        SystemConfigurationValues values = loader.load();

        assertThat(values.defaultSosMessage()).isEqualTo("Necesito ayuda. Estoy en una emergencia.");
        assertThat(values.heartbeatIntervalSeconds()).isEqualTo((short) 60);
        assertThat(values.offlineTimeoutSeconds()).isEqualTo((short) 180);
        assertThat(values.otpTtlMinutes()).isEqualTo((short) 180);
        verify(repository).findByConfigurationId((short) 1);
    }

    @Test
    void rejectsAMissingSingletonWithoutDatabaseDetails() {
        when(repository.findByConfigurationId((short) 1)).thenReturn(Optional.empty());

        assertThatThrownBy(loader::load)
                .isInstanceOf(SystemConfigurationUnavailableException.class)
                .hasMessage("System configuration is unavailable or invalid");
    }

    @Test
    void rejectsInconsistentTimeoutConfiguration() {
        SystemConfigurationEntity invalid = new SystemConfigurationEntity(
                (short) 1, "SOS", (short) 60, (short) 60, (short) 10, 1_048_576,
                (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
        when(repository.findByConfigurationId((short) 1)).thenReturn(Optional.of(invalid));

        assertThatThrownBy(loader::load)
                .isInstanceOf(SystemConfigurationUnavailableException.class)
                .hasMessage("System configuration is unavailable or invalid");
    }

    private SystemConfigurationEntity validConfiguration() {
        return new SystemConfigurationEntity(
                (short) 1, "Necesito ayuda. Estoy en una emergencia.", (short) 60, (short) 180,
                (short) 10, 1_048_576, (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
    }
}
