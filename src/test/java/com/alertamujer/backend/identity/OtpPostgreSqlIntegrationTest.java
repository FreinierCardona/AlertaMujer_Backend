package com.alertamujer.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.service.OtpService;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import java.time.Instant;
import java.sql.Timestamp;
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

/** Verifies the deployed grants, hash functions, and atomic registration flow. */
class OtpPostgreSqlIntegrationTest {

    @Test
    void persistsHashedSmsOtpAndCreatesTheAccountOnlyAfterBothChannelsAreVerified() {
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
            RegistrationRequestService registrationService = context.getBean(RegistrationRequestService.class);
            OtpService otpService = context.getBean(OtpService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            String suffix = String.format("%010d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L));
            RegistrationRequestResponse registration = null;
            UUID userId = null;

            try {
                registration = registrationService.startPublicRegistration(new RegistrationRequestInput(
                        "@otp" + suffix, "Ana", "Perez", "otp-" + suffix + "@example.com",
                        "3" + suffix.substring(0, 9), "SecurePass#2026", true));
                UUID registrationRequestId = registration.registrationRequestId();
                OtpIssuedResponse sms = otpService.issueRegistrationCode(
                        registrationRequestId, OtpChannel.SMS);

                assertThat(sms.simulatedSmsCode()).matches("^[0-9]{6}$");
                RegistrationVerificationResponse pending = otpService.verifyRegistrationCode(
                        registrationRequestId, OtpChannel.SMS, sms.simulatedSmsCode());
                assertThat(pending.status()).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("select count(*) from identity.users where username = ?", Integer.class,
                        "@otp" + suffix)).isZero();
                assertThatThrownBy(() -> jdbc.queryForObject("""
                        select code_hash from identity.user_verification_codes
                         where registration_request_id = ?
                        """, String.class, registrationRequestId))
                        .isInstanceOf(DataAccessException.class);

                String emailCode = "654321";
                jdbc.update("""
                        insert into identity.user_verification_codes (
                            verification_code_id, registration_request_id, channel, purpose, destination_snapshot,
                            code_hash, expires_at, attempt_count, max_attempts, resend_number, created_at, updated_at
                        ) values (?, ?, 'EMAIL', 'EMAIL_VERIFICATION', ?, ?, ?, 0, 5, 0, ?, ?)
                        """, UUID.randomUUID(), registrationRequestId, "otp-" + suffix + "@example.com",
                        encoder.encode(emailCode), Timestamp.from(Instant.now().plusSeconds(3_600)),
                        Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

                RegistrationVerificationResponse completed = otpService.verifyRegistrationCode(
                        registrationRequestId, OtpChannel.EMAIL, emailCode);
                userId = completed.userId();
                assertThat(completed.status()).isEqualTo("COMPLETED");
                assertThat(userId).isNotNull();
                assertThat(jdbc.queryForObject("""
                        select status = 'COMPLETED' from identity.registration_requests
                         where registration_request_id = ?
                        """, Boolean.class, registrationRequestId)).isTrue();
                assertThat(jdbc.queryForObject("""
                        select count(*) from identity.user_credentials where user_id = ?
                        """, Integer.class, userId)).isEqualTo(1);
            } finally {
                if (userId != null) {
                    jdbc.update("delete from identity.users where user_id = ?", userId);
                }
                if (registration != null) {
                    jdbc.update("delete from identity.registration_requests where registration_request_id = ?",
                            registration.registrationRequestId());
                }
            }
        }
    }
}
