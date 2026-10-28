package com.alertamujer.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Carga una sola vez al iniciar la configuración versionada con la entrega. */
@Configuration
class SystemConfigurationStartupConfiguration {

    @Bean
    SystemConfigurationValues systemConfigurationValues(SystemConfigurationLoader loader) {
        return loader.load();
    }
}
