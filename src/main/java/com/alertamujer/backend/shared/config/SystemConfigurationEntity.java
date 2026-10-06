package com.alertamujer.backend.shared.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of the singleton supplied by HU-DB-008.
 *
 * <p>The application role only has SELECT permission for this table. Changes
 * are versioned in the database repository, never made by this application.</p>
 */
@Entity
@Immutable
@Table(name = "system_configuration", schema = "configuration")
class SystemConfigurationEntity {

    @Id
    @Column(name = "configuration_id", nullable = false)
    private Short configurationId;

    @Column(name = "default_sos_message", nullable = false, length = 500)
    private String defaultSosMessage;

    @Column(name = "heartbeat_interval_seconds", nullable = false)
    private Short heartbeatIntervalSeconds;

    @Column(name = "offline_timeout_seconds", nullable = false)
    private Short offlineTimeoutSeconds;

    @Column(name = "max_evidence_count", nullable = false)
    private Short maxEvidenceCount;

    @Column(name = "max_evidence_size_bytes", nullable = false)
    private Integer maxEvidenceSizeBytes;

    @Column(name = "max_chat_message_length", nullable = false)
    private Short maxChatMessageLength;

    @Column(name = "otp_ttl_minutes", nullable = false)
    private Short otpTtlMinutes;

    @Column(name = "otp_max_attempts", nullable = false)
    private Short otpMaxAttempts;

    @Column(name = "otp_max_resends", nullable = false)
    private Short otpMaxResends;

    @Column(name = "otp_resend_cooldown_minutes", nullable = false)
    private Short otpResendCooldownMinutes;

    protected SystemConfigurationEntity() {
    }

    SystemConfigurationEntity(Short configurationId, String defaultSosMessage,
            Short heartbeatIntervalSeconds, Short offlineTimeoutSeconds,
            Short maxEvidenceCount, Integer maxEvidenceSizeBytes,
            Short maxChatMessageLength, Short otpTtlMinutes, Short otpMaxAttempts,
            Short otpMaxResends, Short otpResendCooldownMinutes) {
        this.configurationId = configurationId;
        this.defaultSosMessage = defaultSosMessage;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.offlineTimeoutSeconds = offlineTimeoutSeconds;
        this.maxEvidenceCount = maxEvidenceCount;
        this.maxEvidenceSizeBytes = maxEvidenceSizeBytes;
        this.maxChatMessageLength = maxChatMessageLength;
        this.otpTtlMinutes = otpTtlMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpMaxResends = otpMaxResends;
        this.otpResendCooldownMinutes = otpResendCooldownMinutes;
    }

    Short getConfigurationId() { return configurationId; }
    String getDefaultSosMessage() { return defaultSosMessage; }
    Short getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    Short getOfflineTimeoutSeconds() { return offlineTimeoutSeconds; }
    Short getMaxEvidenceCount() { return maxEvidenceCount; }
    Integer getMaxEvidenceSizeBytes() { return maxEvidenceSizeBytes; }
    Short getMaxChatMessageLength() { return maxChatMessageLength; }
    Short getOtpTtlMinutes() { return otpTtlMinutes; }
    Short getOtpMaxAttempts() { return otpMaxAttempts; }
    Short getOtpMaxResends() { return otpMaxResends; }
    Short getOtpResendCooldownMinutes() { return otpResendCooldownMinutes; }
}
