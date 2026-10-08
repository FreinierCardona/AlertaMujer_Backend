package com.alertamujer.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.administration.service.AdministrationService;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.evidence.service.EvidenceService;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.service.OtpService;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Validates HU-API-018 internal work against the migrated application-role database. */
class InternalJobsPostgreSqlIntegrationTest {
    @Test
    void performsBoundedIdentityCleanupTimeoutInactivityAndOrphanReconciliation() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        Path storage = Files.createTempDirectory(Path.of("target"), "internal-jobs-");
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password,
                "evidence.storage-path", storage.toString(), "spring.main.banner-mode", "off"));

        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            OtpService otp = context.getBean(OtpService.class);
            RegistrationRequestService registrations = context.getBean(RegistrationRequestService.class);
            EmergencyService emergencies = context.getBean(EmergencyService.class);
            AdministrationService administration = context.getBean(AdministrationService.class);
            EvidenceService evidence = context.getBean(EvidenceService.class);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID inactiveUser = UUID.randomUUID();
            UUID sosOwner = UUID.randomUUID();
            UUID emergency = UUID.randomUUID();
            RegistrationRequestResponse registration = null;

            try {
                registration = registrations.startPublicRegistration(new RegistrationRequestInput("@purge" + suffix,
                        "Ana", "Perez", "purge-" + suffix + "@example.com", phoneFor(UUID.randomUUID()),
                        "SecurePass#2026", true));
                otp.issueRegistrationCode(registration.registrationRequestId(), OtpChannel.SMS);
                Instant expiredAt = Instant.now().minusSeconds(60);
                jdbc.update("""
                        update identity.registration_requests
                           set created_at = ?, expires_at = ?, updated_at = ?
                         where registration_request_id = ?
                        """, Timestamp.from(expiredAt.minusSeconds(60)), Timestamp.from(expiredAt), Timestamp.from(expiredAt),
                        registration.registrationRequestId());
                assertThat(otp.purgeTemporaryIdentityData()).isPositive();
                assertThat(jdbc.queryForObject("select count(*) from identity.registration_requests where registration_request_id = ?",
                        Integer.class, registration.registrationRequestId())).isZero();

                Instant old = Instant.now().minusSeconds(181);
                insertUser(jdbc, inactiveUser, "@inactive" + suffix, "inactive-" + suffix + "@example.com",
                        phoneFor(inactiveUser), Instant.now().minusSeconds(8_000_000));
                insertUser(jdbc, sosOwner, "@sos" + suffix, "sos-" + suffix + "@example.com",
                        phoneFor(sosOwner), Instant.now());
                jdbc.update("""
                        insert into emergency.emergencies (emergency_id, user_id, status, last_heartbeat_at, message_snapshot,
                            started_at, created_at, updated_at)
                        values (?, ?, 'ACTIVE', ?, 'Help', ?, ?, ?)
                        """, emergency, sosOwner, Timestamp.from(old), Timestamp.from(old), Timestamp.from(old), Timestamp.from(old));
                jdbc.update("""
                        insert into emergency.emergency_status_history (emergency_status_history_id, emergency_id, sequence_no,
                            previous_status, new_status, actor_user_id, occurred_at)
                        values (?, ?, 1, null, 'ACTIVE', ?, ?)
                        """, UUID.randomUUID(), emergency, sosOwner, Timestamp.from(old));

                emergencies.markOfflineIfTimedOut(emergency);
                assertThat(jdbc.queryForObject("select status from emergency.emergencies where emergency_id = ?", String.class, emergency))
                        .isEqualTo("OFFLINE");
                assertThat(jdbc.queryForObject("select previous_operational_status from emergency.emergencies where emergency_id = ?",
                        String.class, emergency)).isEqualTo("ACTIVE");

                assertThat(administration.disableUserIfStillInactive(inactiveUser)).isTrue();
                assertThat(jdbc.queryForObject("select account_status from identity.users where user_id = ?", String.class, inactiveUser))
                        .isEqualTo("DISABLED");
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where subject_user_id = ? and actor_user_id is null "
                        + "and action = 'ACCOUNT_STATUS_CHANGED'", Integer.class, inactiveUser)).isEqualTo(1);

                Path orphan = storage.resolve(UUID.randomUUID() + ".webp");
                Files.write(orphan, new byte[] {1});
                Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minusSeconds(25 * 60 * 60)));
                assertThat(evidence.reconcileOrphans()).isEqualTo(1);
                assertThat(Files.exists(orphan)).isFalse();
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?)", inactiveUser, sosOwner);
                if (registration != null) jdbc.update("delete from identity.registration_requests where registration_request_id = ?",
                        registration.registrationRequestId());
            }
        } finally {
            Files.deleteIfExists(storage);
        }
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String email, String phone, Instant activity) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, last_activity_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?, ?)
                """, id, username, email, phone, Timestamp.from(now), Timestamp.from(activity), Timestamp.from(now), Timestamp.from(now));
    }

    private static String phoneFor(UUID id) {
        return "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L));
    }
}
