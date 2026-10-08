package com.alertamujer.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.identity.dto.request.ContactChangeVerifyInput;
import com.alertamujer.backend.identity.dto.request.ProfileUpdateInput;
import com.alertamujer.backend.identity.dto.request.SosMessageInput;
import com.alertamujer.backend.identity.service.ProfileService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Uses the migrated application role to verify the HU-API-010 SQL/grants contract. */
class ProfilePostgreSqlIntegrationTest {
    @Test
    void updatesOnlyOwnStateConsumesContactOtpAcceptsTermsAndDeletesTheRoot() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        Path evidenceDirectory = Files.createTempDirectory(Path.of("target"), "profile-evidence-");
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "spring.main.banner-mode", "off",
                "evidence.storage-path", evidenceDirectory.toString()));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ProfileService service = context.getBean(ProfileService.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            Instant now = Instant.now();
            String suffix = userId.toString().substring(0, 8);
            String newEmail = "profile-new-" + suffix + "@example.com";
            try {
                insertUser(jdbc, userId, "@profile" + suffix, "profile-" + suffix + "@example.com", phone(userId),
                        "SELF_REGISTERED", now);
                jdbc.update("""
                        insert into identity.user_sessions (session_id, user_id, refresh_token_hash, client_type, last_used_at, expires_at, created_at)
                        values (?, ?, 'not-a-real-token', 'MOBILE', ?, ?, ?)
                        """, sessionId, userId, Timestamp.from(now), Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now));
                AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, sessionId, "USER", false);

                assertThat(service.updateProfile(identity, new ProfileUpdateInput("@perfil" + suffix, "Ana Maria", null)).username())
                        .isEqualTo("@perfil" + suffix);
                assertThat(service.saveEmergencySettings(identity, new SosMessageInput("  Mensaje seguro  ")).message())
                        .isEqualTo("Mensaje seguro");
                UUID otpId = UUID.randomUUID();
                jdbc.update("""
                        insert into identity.user_verification_codes (verification_code_id, user_id, channel, purpose,
                          destination_snapshot, code_hash, expires_at, attempt_count, max_attempts, resend_number, created_at, updated_at)
                        values (?, ?, 'EMAIL', 'PROFILE_CONTACT_CHANGE', ?, ?, ?, 0, 5, 0, ?, ?)
                        """, otpId, userId, newEmail, encoder.encode("123456"), Timestamp.from(now.plusSeconds(300)),
                        Timestamp.from(now), Timestamp.from(now));
                service.verifyContactChange(identity, new ContactChangeVerifyInput("email", newEmail, "123456"));
                assertThat(jdbc.queryForObject("select email::text from identity.users where user_id = ?", String.class, userId))
                        .isEqualTo(newEmail);
                assertThat(jdbc.queryForObject("select revoked_at is not null from identity.user_sessions where session_id = ?",
                        Boolean.class, sessionId)).isTrue();

                insertUser(jdbc, adminId, "@admin" + suffix, "admin-" + suffix + "@example.com", phone(adminId),
                        "ADMIN_CREATED", now);
                service.acceptTerms(new AuthenticatedIdentity(adminId, UUID.randomUUID(), "USER", true));
                assertThat(jdbc.queryForObject("select accepted_terms_at is not null from identity.users where user_id = ?",
                        Boolean.class, adminId)).isTrue();

                String fileReference = UUID.randomUUID() + ".webp";
                Files.createFile(evidenceDirectory.resolve(fileReference));
                UUID emergencyId = UUID.randomUUID();
                jdbc.update("""
                        insert into emergency.emergencies (emergency_id, user_id, status, message_snapshot, started_at, finalized_at,
                          created_at, updated_at)
                        values (?, ?, 'FINALIZED', 'Necesito ayuda', ?, ?, ?, ?)
                        """, emergencyId, userId, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now.minusSeconds(1)),
                        Timestamp.from(now), Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergency_evidences (evidence_id, emergency_id, evidence_sequence, file_reference,
                          mime_type, file_size_bytes, received_at)
                        values (?, ?, 1, ?, 'image/webp', 1, ?)
                        """, UUID.randomUUID(), emergencyId, fileReference, Timestamp.from(now));

                service.deleteAccount(identity);
                assertThat(jdbc.queryForObject("select exists (select 1 from identity.users where user_id = ?)", Boolean.class, userId))
                        .isFalse();
                assertThat(Files.exists(evidenceDirectory.resolve(fileReference))).isFalse();
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?)", userId, adminId);
            }
        } finally {
            Files.deleteIfExists(evidenceDirectory);
        }
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String email, String phone,
            String origin, Instant now) {
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                  account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', ?, ?, ?, ?)
                """, id, username, email, phone, origin,
                "SELF_REGISTERED".equals(origin) ? Timestamp.from(now) : null, Timestamp.from(now), Timestamp.from(now));
    }

    private static String phone(UUID id) {
        return "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L));
    }
}
