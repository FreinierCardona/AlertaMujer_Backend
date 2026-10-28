package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.backend.identity.dto.request.PasswordResetConfirmInput;
import com.alertamujer.backend.identity.repository.IdentityAuthenticationRepository;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.audit.AuditService;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.security.JwtAccessTokenService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Verifica commits y reversiones reales con el rol de aplicación y el esquema PostgreSQL migrado. */
class PasswordResetPostgreSqlIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private AuthenticationService service;
    private PasswordEncoder encoder;
    private UUID userId;
    private UUID codeId;
    private UUID sessionId;
    private String email;
    private String originalHash;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(System.getenv("SPRING_DATASOURCE_URL") != null
                && System.getenv("SPRING_DATASOURCE_USERNAME") != null
                && System.getenv("SPRING_DATASOURCE_PASSWORD") != null,
                "Se requiere una base PostgreSQL migrada para verificar las transacciones");
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(AuthenticationService.class);
        encoder = context.getBean(PasswordEncoder.class);
        userId = UUID.randomUUID();
        codeId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        String suffix = userId.toString().substring(0, 8);
        email = "reset-" + suffix + "@example.test";
        String phone = "3" + String.format("%09d", Math.floorMod(userId.getLeastSignificantBits(), 1_000_000_000L));
        Instant created = Instant.now().minusSeconds(10);
        originalHash = encoder.encode("Original#Password2026");
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone,
                    role, account_status, account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Prueba', 'Recuperacion', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, userId, "@reset" + suffix, email, phone,
                Timestamp.from(created), Timestamp.from(created), Timestamp.from(created));
        jdbc.update("""
                insert into identity.user_credentials (credential_id, user_id, password_hash,
                    password_updated_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, originalHash,
                Timestamp.from(created), Timestamp.from(created), Timestamp.from(created));
        jdbc.update("""
                insert into identity.user_verification_codes (verification_code_id, user_id, channel, purpose,
                    destination_snapshot, code_hash, expires_at, created_at, updated_at)
                values (?, ?, 'EMAIL', 'PASSWORD_RESET', ?, ?, ?, ?, ?)
                """, codeId, userId, email, encoder.encode("123456"), Timestamp.from(created.plusSeconds(600)),
                Timestamp.from(created), Timestamp.from(created));
        jdbc.update("""
                insert into identity.user_sessions (session_id, user_id, refresh_token_hash, client_type,
                    last_used_at, expires_at, created_at) values (?, ?, ?, 'MOBILE', ?, ?, ?)
                """, sessionId, userId, encoder.encode(UUID.randomUUID().toString()),
                Timestamp.from(created), Timestamp.from(created.plusSeconds(600)), Timestamp.from(created));
    }

    @AfterEach
    void tearDown() {
        if (context == null) return;
        try {
            if (userId != null) jdbc.update("delete from identity.users where user_id = ?", userId);
        } finally {
            context.close();
        }
    }

    @Test
    void commitsEachFailedAttemptAndRejectsTheCorrectCodeAfterFiveFailures() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> reset("654321", "Replacement#Password2026"))
                    .isInstanceOf(RuleViolationException.class);
            // La lectura usa otra transacción, después de salir del proxy del servicio.
            assertThat(attemptCount()).isEqualTo(attempt);
            assertThat(codeDate("invalidated_at") != null).isEqualTo(attempt == 5);
            assertCredentialsAndSessionUnchanged();
        }
        assertThatThrownBy(() -> reset("123456", "Replacement#Password2026"))
                .isInstanceOf(RuleViolationException.class);
        assertThat(attemptCount()).isEqualTo(5);
        assertThat(codeDate("used_at")).isNull();
        assertCredentialsAndSessionUnchanged();
    }

    @Test
    void validCodeChangesThePasswordConsumesTheCodeAndRevokesTheSession() {
        assertThatThrownBy(() -> reset("654321", "Replacement#Password2026"))
                .isInstanceOf(RuleViolationException.class);
        reset("123456", "Replacement#Password2026");
        assertThat(attemptCount()).isEqualTo(1);
        assertThat(codeDate("used_at")).isNotNull();
        assertThat(codeDate("invalidated_at")).isNull();
        assertThat(encoder.matches("Replacement#Password2026", passwordHash())).isTrue();
        assertThat(sessionRevokedAt()).isNotNull();
        assertThatThrownBy(() -> reset("123456", "Another#Password2026"))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void unexpectedFailureAfterConsumingTheCodeRollsBackAllChanges() {
        // Provoca un fallo de persistencia después de consumir el código, fuera del contrato HTTP validado.
        assertThatThrownBy(() -> reset("123456", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(codeDate("used_at")).isNull();
        assertThat(attemptCount()).isZero();
        assertCredentialsAndSessionUnchanged();
        reset("123456", "Replacement#Password2026");
        assertThat(codeDate("used_at")).isNotNull();
    }

    private void reset(String code, String password) {
        service.confirmPasswordReset(new PasswordResetConfirmInput(email, code, password));
    }

    private int attemptCount() {
        return jdbc.queryForObject("select attempt_count from identity.user_verification_codes where verification_code_id = ?",
                Integer.class, codeId);
    }

    private Timestamp codeDate(String column) {
        return jdbc.queryForObject("select " + column + " from identity.user_verification_codes where verification_code_id = ?",
                Timestamp.class, codeId);
    }

    private String passwordHash() {
        return jdbc.queryForObject("select identity.get_user_password_hash(?)", String.class, userId);
    }

    private Timestamp sessionRevokedAt() {
        return jdbc.queryForObject("select revoked_at from identity.user_sessions where session_id = ?", Timestamp.class, sessionId);
    }

    private void assertCredentialsAndSessionUnchanged() {
        assertThat(passwordHash()).isEqualTo(originalHash);
        assertThat(sessionRevokedAt()).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("SPRING_DATASOURCE_URL"),
                    System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean
        PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

        @Bean
        AuthenticationService authenticationService(JdbcTemplate jdbc, PasswordEncoder encoder) {
            return new AuthenticationServiceImpl(new IdentityAuthenticationRepository(jdbc), encoder,
                    new JwtAccessTokenService("test-only-reset-secret-with-at-least-32-bytes"), Clock.systemUTC(), AuditService.noop());
        }
    }
}
