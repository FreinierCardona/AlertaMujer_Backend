package com.alertamujer.backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs only when a migrated PostgreSQL database is supplied through the same
 * environment variables used by the application. It never provisions a schema
 * or connects with an owner or migrator account.
 */
class PostgreSqlIntegrationTest {

    @Test
    void startsAgainstTheMigratedDatabaseAndConfirmsApplicationPermissions() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");

        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.profiles.active", "test",
                "spring.datasource.url", url,
                "spring.datasource.username", username,
                "spring.datasource.password", password,
                "spring.main.banner-mode", "off"));

        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.liquibase.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("alertamujer_app");
            assertThat(jdbc.queryForObject("""
                    select count(*)
                    from information_schema.schemata
                    where schema_name in ('configuration', 'identity', 'profile', 'contacts',
                                          'emergency', 'notification', 'audit')
                    """, Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("select has_schema_privilege(current_user, 'identity', 'CREATE')", Boolean.class))
                    .isFalse();
            assertThat(jdbc.queryForObject(
                    "select has_table_privilege(current_user, 'configuration.system_configuration', 'SELECT')",
                    Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject(
                    "select has_table_privilege(current_user, 'configuration.system_configuration', 'UPDATE')",
                    Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject(
                    "select has_table_privilege(current_user, 'public.databasechangelog', 'UPDATE')",
                    Boolean.class)).isFalse();
        }
    }
}
