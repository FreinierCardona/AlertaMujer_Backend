package com.alertamujer.backend.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.administration.service.AdministrationService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises administrative status/audit behavior against the migrated application role when supplied. */
class AdministrationPostgreSqlIntegrationTest {

    @Test
    void appendsTheWhitelistedStatusEventWithoutUpdateOrDeleteAuditPrivileges() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password,
                "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            AdministrationService service = context.getBean(AdministrationService.class);
            UUID target = UUID.randomUUID();
            UUID createdAdmin = null;
            UUID administrator = jdbc.query("select user_id from identity.users where role = 'ENTITY_ADMIN' limit 1",
                    (rs, row) -> rs.getObject(1, UUID.class)).stream().findFirst().orElse(null);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            try {
                if (administrator == null) {
                    createdAdmin = UUID.randomUUID();
                    administrator = createdAdmin;
                    insertUser(jdbc, administrator, "@admin" + suffix, suffix, 1, "ENTITY_ADMIN", "ENABLED", Instant.now());
                }
                Instant inactiveSince = Instant.now().atZone(ZoneOffset.UTC).minusMonths(2).minusSeconds(1).toInstant();
                insertUser(jdbc, target, "@target" + suffix, suffix, 2, "USER", "ENABLED", inactiveSince);

                var response = service.changeStatus(new AuthenticatedIdentity(administrator, UUID.randomUUID(),
                        "ENTITY_ADMIN", false), target, "DISABLED");

                assertThat(response.accountStatus()).isEqualTo("DISABLED");
                assertThat(jdbc.queryForObject("select account_status from identity.users where user_id = ?", String.class, target))
                        .isEqualTo("DISABLED");
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and "
                        + "subject_user_id = ? and action = 'ACCOUNT_STATUS_CHANGED'", Integer.class, administrator, target))
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'audit.audit_logs', 'UPDATE')", Boolean.class))
                        .isFalse();
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'audit.audit_logs', 'DELETE')", Boolean.class))
                        .isFalse();
            } finally {
                jdbc.update("delete from identity.users where user_id = ?", target);
                if (createdAdmin != null) jdbc.update("delete from identity.users where user_id = ?", createdAdmin);
            }
        }
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String suffix, int number,
            String role, String status, Instant activity) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, last_activity_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, ?, ?, 'SELF_REGISTERED', ?, ?, ?, ?)
                """, id, username, "admin-" + number + "-" + suffix + "@example.com",
                "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)), role, status,
                Timestamp.from(now), Timestamp.from(activity), Timestamp.from(now), Timestamp.from(now));
    }
}
