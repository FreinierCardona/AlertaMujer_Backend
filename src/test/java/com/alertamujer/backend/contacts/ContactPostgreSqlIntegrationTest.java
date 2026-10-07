package com.alertamujer.backend.contacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.contacts.dto.request.ContactInvitationInput;
import com.alertamujer.backend.contacts.service.ContactService;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
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

/** Verifies the HU-API-011 state machine and canonical-pair constraint with the application role. */
class ContactPostgreSqlIntegrationTest {

    @Test
    void preservesOneCanonicalRelationshipAcrossInvitationStateChangesAndEligibility() throws Exception {
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
            ContactService service = context.getBean(ContactService.class);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID third = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String firstUsername = "@contacta" + suffix;
            String secondUsername = "@contactb" + suffix;
            String thirdUsername = "@contactc" + suffix;
            try {
                insertUser(jdbc, first, firstUsername, suffix, 1);
                insertUser(jdbc, second, secondUsername, suffix, 2);
                insertUser(jdbc, third, thirdUsername, suffix, 3);
                AuthenticatedIdentity firstIdentity = identity(first);
                AuthenticatedIdentity secondIdentity = identity(second);
                AuthenticatedIdentity thirdIdentity = identity(third);

                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                    List<Callable<ContactService.InvitationResult>> calls = List.of(
                            () -> service.invite(firstIdentity, new ContactInvitationInput(secondUsername)),
                            () -> service.invite(secondIdentity, new ContactInvitationInput(firstUsername)));
                    List<Future<ContactService.InvitationResult>> outcomes = executor.invokeAll(calls);
                    assertThat(outcomes.get(0).get().invitation().status()).isEqualTo("PENDING");
                    assertThat(outcomes.get(1).get().invitation().status()).isEqualTo("PENDING");
                } finally {
                    executor.shutdownNow();
                }

                assertThat(jdbc.queryForObject("""
                        select count(*) from contacts.emergency_contacts
                         where least(owner_user_id, contact_user_id) = least(?, ?)
                           and greatest(owner_user_id, contact_user_id) = greatest(?, ?)
                        """, Integer.class, first, second, first, second)).isEqualTo(1);
                UUID firstContactId = jdbc.queryForObject("""
                        select contact_id from contacts.emergency_contacts
                         where least(owner_user_id, contact_user_id) = least(?, ?)
                           and greatest(owner_user_id, contact_user_id) = greatest(?, ?)
                        """, UUID.class, first, second, first, second);
                UUID recipient = jdbc.queryForObject("select contact_user_id from contacts.emergency_contacts where contact_id = ?",
                        UUID.class, firstContactId);
                service.accept(recipient.equals(first) ? firstIdentity : secondIdentity, firstContactId);
                assertThat(jdbc.queryForObject("select relationship_status from contacts.emergency_contacts where contact_id = ?",
                        String.class, firstContactId)).isEqualTo("ACCEPTED");
                assertThat(service.ownContacts(firstIdentity, 0, 20).items()).anyMatch(contact ->
                        contact.contactId().equals(firstContactId) && contact.eligible());

                jdbc.update("update identity.users set account_status = 'DISABLED', disabled_at = ?, updated_at = ? where user_id = ?",
                        Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), second);
                assertThat(service.ownContacts(firstIdentity, 0, 20).items()).anyMatch(contact ->
                        contact.contactId().equals(firstContactId) && !contact.eligible());

                var invitation = service.invite(firstIdentity, new ContactInvitationInput(thirdUsername));
                jdbc.update("""
                        update contacts.emergency_contacts
                           set created_at = current_timestamp - interval '2 days', expires_at = current_timestamp - interval '1 day',
                               updated_at = current_timestamp
                         where contact_id = ?
                        """, invitation.invitation().contactId());
                var reinvited = service.reinvite(firstIdentity, invitation.invitation().contactId());
                assertThat(reinvited.status()).isEqualTo("PENDING");
                service.reject(thirdIdentity, reinvited.contactId());
                assertThatThrownBy(() -> service.reinvite(firstIdentity, reinvited.contactId()))
                        .isInstanceOf(StateConflictException.class);
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?, ?)", first, second, third);
            }
        }
    }

    private static AuthenticatedIdentity identity(UUID userId) {
        return new AuthenticatedIdentity(userId, UUID.randomUUID(), "USER", false);
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String suffix, int number) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                  account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, id, username, "contacts-" + number + "-" + suffix + "@example.com",
                "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
