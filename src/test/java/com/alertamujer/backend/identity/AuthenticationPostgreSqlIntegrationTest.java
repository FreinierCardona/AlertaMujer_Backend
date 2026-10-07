package com.alertamujer.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.identity.dto.request.LoginInput;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Exercises the session grants and protected refresh-hash function against a supplied migrated database. */
class AuthenticationPostgreSqlIntegrationTest {

    @Test
    void storesOnlyTheRefreshHashAndRejectsItsReuseAfterRotationAndLogout() {
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
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            AuthenticationService service = context.getBean(AuthenticationService.class);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID userId = UUID.randomUUID();
            String email = "auth-" + suffix + "@example.com";
            String phone = "3" + String.format("%09d", Math.floorMod(userId.getLeastSignificantBits(), 1_000_000_000L));
            String rawPassword = "SecurePass#2026";
            try {
                Instant createdAt = Instant.now();
                jdbc.update("""
                        insert into identity.users (
                            user_id, username, first_names, last_names, email, phone, role, account_status,
                            account_origin, accepted_terms_at, created_at, updated_at
                        ) values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                        """, userId, "@auth" + suffix, email, phone,
                        Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
                jdbc.update("""
                        insert into identity.user_credentials (
                            credential_id, user_id, password_hash, password_updated_at, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), userId, encoder.encode(rawPassword), Timestamp.from(createdAt),
                        Timestamp.from(createdAt), Timestamp.from(createdAt));

                var login = service.login(new LoginInput(email, rawPassword));
                UUID sessionId = UUID.fromString(login.refreshToken().substring(0, login.refreshToken().indexOf('.')));
                assertThatThrownBy(() -> jdbc.queryForObject(
                        "select refresh_token_hash from identity.user_sessions where session_id = ?", String.class, sessionId))
                        .isInstanceOf(DataAccessException.class);
                String storedHash = jdbc.queryForObject(
                        "select identity.get_user_session_refresh_token_hash(?)", String.class, sessionId);
                assertThat(storedHash).isNotEqualTo(login.refreshToken());

                var refreshed = service.refresh(login.refreshToken());
                assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
                assertThatThrownBy(() -> service.refresh(login.refreshToken())).isInstanceOf(UnauthorizedException.class);

                service.logout(new AuthenticatedIdentity(userId, sessionId, "USER", false));
                assertThatThrownBy(() -> service.refresh(refreshed.refreshToken())).isInstanceOf(UnauthorizedException.class);
            } finally {
                jdbc.update("delete from identity.users where user_id = ?", userId);
            }
        }
    }
}
