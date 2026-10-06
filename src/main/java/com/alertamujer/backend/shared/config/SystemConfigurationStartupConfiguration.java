package com.alertamujer.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the release-versioned configuration exactly once during startup. */
@Configuration
class SystemConfigurationStartupConfiguration {

    @Bean
    SystemConfigurationValues systemConfigurationValues(SystemConfigurationLoader loader) {
        return loader.load();
    }
}
