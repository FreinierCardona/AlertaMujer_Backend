package com.alertamujer.backend.shared.config;

/** Fallo seguro de inicio que no revela detalles de conexión ni de la base de datos. */
class SystemConfigurationUnavailableException extends IllegalStateException {

    SystemConfigurationUnavailableException() {
        super("System configuration is unavailable or invalid");
    }
}
