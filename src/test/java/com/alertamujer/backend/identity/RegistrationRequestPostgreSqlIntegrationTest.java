package com.alertamujer.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uses the application role against a supplied migrated database and removes
 * its random temporary requests before returning.
 */
class RegistrationRequestPostgreSqlIntegrationTest {

    @Test
    void persistsOnlyTemporaryRequestsWithTheDatabaseLeastPrivilegeContract() {
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
            RegistrationRequestService service = context.getBean(RegistrationRequestService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String numericSuffix = String.format("%010d",
                    Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L));
            String publicUsername = "@u" + numericSuffix;
            String adminUsername = "@a" + numericSuffix;
            RegistrationRequestResponse publicRequest = null;
            RegistrationRequestResponse adminRequest = null;

            try {
                publicRequest = service.startPublicRegistration(new RegistrationRequestInput(
                        publicUsername, "Ana", "Perez", "public-" + numericSuffix + "@example.com",
                        "3" + numericSuffix.substring(0, 9), "SecurePass#2026", true));
                adminRequest = service.startAdministrativeRegistration(new AdminRegistrationRequestInput(
                        adminUsername, "Bea", "Lopez", "admin-" + numericSuffix + "@example.com",
                        "3" + numericSuffix.substring(1, 10), "SecurePass#2026"));

                assertThat(jdbc.queryForObject("""
                        select account_origin = 'SELF_REGISTERED' and accepted_terms_at is not null and status = 'PENDING'
                        from identity.registration_requests where registration_request_id = ?
                        """, Boolean.class, publicRequest.registrationRequestId())).isTrue();
                assertThat(jdbc.queryForObject("""
                        select account_origin = 'ADMIN_CREATED' and accepted_terms_at is null and status = 'PENDING'
                        from identity.registration_requests where registration_request_id = ?
                        """, Boolean.class, adminRequest.registrationRequestId())).isTrue();
                assertThat(jdbc.queryForObject(
                        "select count(*) from identity.users where username in (?, ?)", Integer.class,
                        publicUsername, adminUsername)).isZero();
                assertThat(jdbc.queryForObject("""
                        select count(*) from identity.user_credentials credentials
                        join identity.users users on users.user_id = credentials.user_id
                        where users.username in (?, ?)
                        """, Integer.class, publicUsername, adminUsername)).isZero();
                assertThat(jdbc.queryForObject("""
                        select count(*) from identity.user_sessions sessions
                        join identity.users users on users.user_id = sessions.user_id
                        where users.username in (?, ?)
                        """, Integer.class, publicUsername, adminUsername)).isZero();
                UUID publicRequestId = publicRequest.registrationRequestId();
                assertThatThrownBy(() -> jdbc.queryForObject("""
                        select password_hash from identity.registration_requests where registration_request_id = ?
                        """, String.class, publicRequestId))
                        .isInstanceOf(DataAccessException.class);
            } finally {
                if (publicRequest != null) {
                    jdbc.update("delete from identity.registration_requests where registration_request_id = ?",
                            publicRequest.registrationRequestId());
                }
                if (adminRequest != null) {
                    jdbc.update("delete from identity.registration_requests where registration_request_id = ?",
                            adminRequest.registrationRequestId());
                }
            }
        }
    }
}
