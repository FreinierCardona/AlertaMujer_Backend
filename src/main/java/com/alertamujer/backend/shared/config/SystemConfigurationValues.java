package com.alertamujer.backend.shared.config;

/**
 * Immutable operational values loaded once from the database release.
 * Future module services inject this type instead of duplicating limits.
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
