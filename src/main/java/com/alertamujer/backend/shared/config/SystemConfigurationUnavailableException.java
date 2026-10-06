package com.alertamujer.backend.shared.config;

/** Safe startup failure that does not disclose connection or database details. */
class SystemConfigurationUnavailableException extends IllegalStateException {

    SystemConfigurationUnavailableException() {
        super("System configuration is unavailable or invalid");
    }
}
