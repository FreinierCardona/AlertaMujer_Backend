package com.alertamujer.backend.shared.config;

/**
 * Valores operativos inmutables cargados una sola vez desde la versión de base de datos. Los futuros
 * servicios de módulos inyectan este tipo en lugar de duplicar los límites.
 */
public record SystemConfigurationValues(
        String defaultSosMessage,
        short heartbeatIntervalSeconds,
        short offlineTimeoutSeconds,
        short maxEvidenceCount,
        int maxEvidenceSizeBytes,
        short maxChatMessageLength,
        short otpTtlMinutes,
        short otpMaxAttempts,
        short otpMaxResends,
        short otpResendCooldownMinutes) {

    public SystemConfigurationValues {
        if (defaultSosMessage == null || defaultSosMessage.isBlank()
                || heartbeatIntervalSeconds <= 0
                || offlineTimeoutSeconds <= heartbeatIntervalSeconds
                || maxEvidenceCount <= 0
                || maxEvidenceSizeBytes <= 0
                || maxChatMessageLength <= 0
                || otpTtlMinutes <= 0
                || otpMaxAttempts <= 0
                || otpMaxResends <= 0
                || otpResendCooldownMinutes <= 0) {
            throw new SystemConfigurationUnavailableException();
        }
    }
}
