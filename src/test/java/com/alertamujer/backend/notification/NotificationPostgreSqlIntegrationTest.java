package com.alertamujer.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.notification.dto.request.DeviceTokenInput;
import com.alertamujer.backend.notification.service.NotificationService;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
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

/** Exercises HU-API-014 against the migrated application grants without contacting the real FCM provider. */
class NotificationPostgreSqlIntegrationTest {
    @Test
    void persistsNoTokenAndDeterministicallySelectedPendingAttemptsAfterTheSosCommit() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        try (ConfigurableApplicationContext context = application(url, username, password).run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            EmergencyService emergencies = context.getBean(EmergencyService.class);
            UUID owner = UUID.randomUUID();
            UUID noTokenContact = UUID.randomUUID();
            UUID tokenContact = UUID.randomUUID();
            UUID lowerTokenId = UUID.fromString("00000000-0000-0000-0000-000000000101");
            UUID selectedTokenId = UUID.fromString("00000000-0000-0000-0000-000000000102");
            String suffix = owner.toString().substring(0, 8);
            try {
                insertUser(jdbc, owner, "@notifyowner" + suffix);
                insertUser(jdbc, noTokenContact, "@notifynone" + suffix);
                insertUser(jdbc, tokenContact, "@notifytoken" + suffix);
                insertAcceptedContact(jdbc, owner, noTokenContact);
                insertAcceptedContact(jdbc, owner, tokenContact);
                Instant sameLastSeen = Instant.parse("2026-10-07T18:00:00Z");
                insertToken(jdbc, lowerTokenId, tokenContact, "token-low-" + suffix, sameLastSeen);
                insertToken(jdbc, selectedTokenId, tokenContact, "token-high-" + suffix, sameLastSeen);

                UUID emergencyId = emergencies.createOrRecover(identity(owner), input()).emergency().emergencyId();

                assertThat(jdbc.query("""
                        select contact_user_id, device_token_id, result_status
                          from notification.emergency_notification_attempts
                         where emergency_id = ? order by contact_user_id
                        """, (rs, row) -> new AttemptRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3)), emergencyId)).containsExactlyInAnyOrder(
                                new AttemptRow(noTokenContact, null, "NO_TOKEN"),
                                new AttemptRow(tokenContact, selectedTokenId, "PENDING"));
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?, ?)", owner, noTokenContact, tokenContact);
            }
        }
    }

    @Test
    void letsTheOwnerReactivateATokenButKeepsTheOtherOwnerConflictGeneric() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        try (ConfigurableApplicationContext context = application(url, username, password).run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            NotificationService tokens = context.getBean(NotificationService.class);
            UUID firstOwner = UUID.randomUUID();
            UUID secondOwner = UUID.randomUUID();
            String suffix = firstOwner.toString().substring(0, 8);
            String token = "token-reusable-" + suffix;
            try {
                insertUser(jdbc, firstOwner, "@tokfirst" + suffix);
                insertUser(jdbc, secondOwner, "@toksecond" + suffix);
                assertThat(tokens.registerDeviceToken(identity(firstOwner), new DeviceTokenInput(token,
                        DeviceTokenInput.Platform.ANDROID))).isTrue();
                jdbc.update("update notification.user_device_tokens set is_active = false, invalidated_at = current_timestamp where fcm_token = ?", token);
                assertThat(tokens.registerDeviceToken(identity(firstOwner), new DeviceTokenInput(token,
                        DeviceTokenInput.Platform.ANDROID))).isFalse();
                assertThat(jdbc.queryForObject("select is_active from notification.user_device_tokens where fcm_token = ?",
                        Boolean.class, token)).isTrue();
                assertThatThrownBy(() -> tokens.registerDeviceToken(identity(secondOwner), new DeviceTokenInput(token,
                        DeviceTokenInput.Platform.ANDROID))).isInstanceOf(StateConflictException.class);
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?)", firstOwner, secondOwner);
            }
        }
    }

    private static SpringApplication application(String url, String username, String password) {
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password,
                "spring.main.banner-mode", "off", "FCM_CREDENTIALS_PATH", ""));
        return application;
    }

    private static EmergencyCreateInput input() {
        return new EmergencyCreateInput(new BigDecimal("4.609710"), new BigDecimal("-74.081750"),
                new BigDecimal("8.5"), Instant.now().minusSeconds(2), null);
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

    private static void insertToken(JdbcTemplate jdbc, UUID tokenId, UUID userId, String token, Instant lastSeenAt) {
        jdbc.update("""
                insert into notification.user_device_tokens (device_token_id, user_id, fcm_token, platform, is_active,
                    last_seen_at, created_at, updated_at)
                values (?, ?, ?, 'ANDROID', true, ?, ?, ?)
                """, tokenId, userId, token, Timestamp.from(lastSeenAt), Timestamp.from(lastSeenAt), Timestamp.from(lastSeenAt));
    }

    private static void insertUser(JdbcTemplate jdbc, UUID userId, String username) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, userId, username, username.substring(1) + "@example.test", phone(userId), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
    }

    private static String phone(UUID id) {
        return "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L));
    }

    private record AttemptRow(UUID contactUserId, UUID deviceTokenId, String resultStatus) { }
}
