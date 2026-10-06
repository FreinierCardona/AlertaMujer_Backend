package com.alertamujer.backend.shared.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SystemConfigurationLoader {

    private static final short SINGLETON_ID = 1;

    private final SystemConfigurationRepository repository;

    SystemConfigurationLoader(SystemConfigurationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    SystemConfigurationValues load() {
        SystemConfigurationEntity configuration = repository.findByConfigurationId(SINGLETON_ID)
                .orElseThrow(SystemConfigurationUnavailableException::new);

        if (!Short.valueOf(SINGLETON_ID).equals(configuration.getConfigurationId())) {
            throw new SystemConfigurationUnavailableException();
        }

        return new SystemConfigurationValues(
                configuration.getDefaultSosMessage(),
                require(configuration.getHeartbeatIntervalSeconds()),
                require(configuration.getOfflineTimeoutSeconds()),
                require(configuration.getMaxEvidenceCount()),
                require(configuration.getMaxEvidenceSizeBytes()),
                require(configuration.getMaxChatMessageLength()),
                require(configuration.getOtpTtlMinutes()),
                require(configuration.getOtpMaxAttempts()),
                require(configuration.getOtpMaxResends()),
                require(configuration.getOtpResendCooldownMinutes()));
    }

    private short require(Short value) {
        if (value == null) {
            throw new SystemConfigurationUnavailableException();
        }
        return value;
    }

    private int require(Integer value) {
        if (value == null) {
            throw new SystemConfigurationUnavailableException();
        }
        return value;
    }
}
