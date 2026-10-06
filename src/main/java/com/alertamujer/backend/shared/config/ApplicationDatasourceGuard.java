package com.alertamujer.backend.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Rejects technical accounts other than the least-privilege application role.
 * Database ownership and migrations belong to the database repository.
 */
@Component
public class ApplicationDatasourceGuard implements ApplicationRunner {

    private static final String APPLICATION_ROLE = "alertamujer_app";

    private final Environment environment;

    public ApplicationDatasourceGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = environment.getRequiredProperty("spring.datasource.username");
        if (!APPLICATION_ROLE.equals(username)) {
            throw new IllegalStateException("The datasource must use the alertamujer_app role");
        }
    }
}
