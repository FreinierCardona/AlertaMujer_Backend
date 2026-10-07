package com.alertamujer.backend.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.request.LocationInput;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Validates HU-API-012 against the migrated PostgreSQL schemas and application grants. */
class EmergencyPostgreSqlIntegrationTest {

    @Test
    void createsAnAtomicSosAndRecoversTheSameRowForRetriesAndConcurrentPosts() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            EmergencyService service = context.getBean(EmergencyService.class);
            UUID firstOwner = UUID.randomUUID();
            UUID firstContact = UUID.randomUUID();
            UUID concurrentOwner = UUID.randomUUID();
            UUID concurrentContact = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            try {
                insertUser(jdbc, firstOwner, "@sosownera" + suffix, suffix, 1);
                insertUser(jdbc, firstContact, "@soscontacta" + suffix, suffix, 2);
                insertUser(jdbc, concurrentOwner, "@sosownerb" + suffix, suffix, 3);
                insertUser(jdbc, concurrentContact, "@soscontactb" + suffix, suffix, 4);
                insertAcceptedContact(jdbc, firstOwner, firstContact);
                insertAcceptedContact(jdbc, concurrentOwner, concurrentContact);

                EmergencyCreateInput input = input();
                var first = service.createOrRecover(identity(firstOwner), input);
                var retry = service.createOrRecover(identity(firstOwner), input);
                assertThat(first.created()).isTrue();
                assertThat(retry.created()).isFalse();
                assertThat(retry.emergency().emergencyId()).isEqualTo(first.emergency().emergencyId());
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergencies where user_id = ?", Integer.class,
                        firstOwner)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_locations where emergency_id = ?",
                        Integer.class, first.emergency().emergencyId())).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_status_history where emergency_id = ?",
                        Integer.class, first.emergency().emergencyId())).isEqualTo(1);
                assertThat(jdbc.queryForObject("select sequence_no from emergency.emergency_status_history where emergency_id = ?",
                        Integer.class, first.emergency().emergencyId())).isEqualTo(1);
                String expectedDefault = jdbc.queryForObject("select default_sos_message from configuration.system_configuration where configuration_id = 1",
                        String.class);
                assertThat(jdbc.queryForObject("select message_snapshot from emergency.emergencies where emergency_id = ?", String.class,
                        first.emergency().emergencyId())).isEqualTo(expectedDefault);

                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                    List<Callable<EmergencyService.CreationResult>> calls = List.of(
                            () -> service.createOrRecover(identity(concurrentOwner), input()),
                            () -> service.createOrRecover(identity(concurrentOwner), input()));
                    List<Future<EmergencyService.CreationResult>> outcomes = executor.invokeAll(calls);
                    var one = outcomes.get(0).get();
                    var two = outcomes.get(1).get();
                    assertThat(List.of(one.created(), two.created())).containsExactlyInAnyOrder(true, false);
                    assertThat(one.emergency().emergencyId()).isEqualTo(two.emergency().emergencyId());
                    UUID emergencyId = one.emergency().emergencyId();
                    assertThat(jdbc.queryForObject("select count(*) from emergency.emergencies where user_id = ?", Integer.class,
                            concurrentOwner)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_locations where emergency_id = ?",
                            Integer.class, emergencyId)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_status_history where emergency_id = ?",
                            Integer.class, emergencyId)).isEqualTo(1);
                } finally {
                    executor.shutdownNow();
                }
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?, ?, ?)", firstOwner, firstContact,
                        concurrentOwner, concurrentContact);
            }
        }
    }

    @Test
    void persistsTheLifecycleWithAppendOnlyHistoryAgainstTheMigratedApplicationGrants() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            EmergencyService service = context.getBean(EmergencyService.class);
            UUID owner = UUID.randomUUID();
            UUID contact = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            try {
                insertUser(jdbc, owner, "@lifeownera" + suffix, suffix, 5);
                insertUser(jdbc, contact, "@lifecontacta" + suffix, suffix, 6);
                insertAcceptedContact(jdbc, owner, contact);
                UUID emergencyId = service.createOrRecover(identity(owner), input()).emergency().emergencyId();

                jdbc.update("update emergency.emergencies set last_heartbeat_at = current_timestamp - interval '181 seconds' where emergency_id = ?",
                        emergencyId);
                service.markOfflineIfTimedOut(emergencyId);
                assertThat(status(jdbc, emergencyId)).isEqualTo("OFFLINE");
                assertThat(jdbc.queryForObject("select previous_operational_status from emergency.emergencies where emergency_id = ?",
                        String.class, emergencyId)).isEqualTo("ACTIVE");

                service.heartbeat(identity(owner), emergencyId, location());
                assertThat(status(jdbc, emergencyId)).isEqualTo("ACTIVE");
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_locations where emergency_id = ?",
                        Integer.class, emergencyId)).isEqualTo(2);

                service.recordLocation(identity(owner), emergencyId, location());
                service.finish(identity(owner), emergencyId);
                assertThat(status(jdbc, emergencyId)).isEqualTo("FINALIZED");
                assertThat(jdbc.query("select sequence_no from emergency.emergency_status_history where emergency_id = ? order by sequence_no",
                        (rs, row) -> rs.getInt(1), emergencyId)).containsExactly(1, 2, 3, 4);
                assertThatThrownBy(() -> service.recordLocation(identity(owner), emergencyId, location()))
                        .isInstanceOf(StateConflictException.class);
                assertThatThrownBy(() -> jdbc.update("delete from emergency.emergency_status_history where emergency_id = ?", emergencyId))
                        .isInstanceOf(Exception.class);
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?)", owner, contact);
            }
        }
    }

    private static EmergencyCreateInput input() {
        return new EmergencyCreateInput(new BigDecimal("4.609710"), new BigDecimal("-74.081750"), new BigDecimal("8.5"),
                Instant.now().minusSeconds(2), null);
    }

    private static LocationInput location() {
        return new LocationInput(new BigDecimal("4.610000"), new BigDecimal("-74.082000"), new BigDecimal("7.5"),
                Instant.now().minusSeconds(1));
    }

    private static String status(JdbcTemplate jdbc, UUID emergencyId) {
        return jdbc.queryForObject("select status from emergency.emergencies where emergency_id = ?", String.class, emergencyId);
    }

    private static AuthenticatedIdentity identity(UUID userId) {
        return new AuthenticatedIdentity(userId, UUID.randomUUID(), "USER", false);
    }

    private static void insertAcceptedContact(JdbcTemplate jdbc, UUID owner, UUID contact) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into contacts.emergency_contacts (contact_id, owner_user_id, contact_user_id, relationship_status,
                    status_changed_at, created_at, updated_at)
                values (?, ?, ?, 'ACCEPTED', ?, ?, ?)
                """, UUID.randomUUID(), owner, contact, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String suffix, int number) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                  account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, id, username, "sos-" + number + "-" + suffix + "@example.com",
                "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
