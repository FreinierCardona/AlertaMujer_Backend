package com.alertamujer.backend.contacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Prueba de autorización HTTP y PostgreSQL para HU-API-022. */
class ContactAlertHttpPostgreSqlIntegrationTest {
    private static final String PASSWORD = "SecurePass#2026";

    @Test
    void exposesOnlyAnAcceptedContactsOpenEmergencyAndRevokesItAfterFinalization() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");
        Path storage = Files.createTempDirectory(Path.of("target"), "contact-alert-http-");
        UUID evidence = UUID.randomUUID();
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "server.port", 0,
                "evidence.storage-path", storage.toString(), "management.health.mail.enabled", false,
                "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run("--evidence.storage-path=" + storage.toAbsolutePath())) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper json = context.getBean(ObjectMapper.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            HttpClient client = HttpClient.newHttpClient();
            String baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/api/v1";
            UUID owner = UUID.randomUUID();
            UUID contact = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();
            UUID emergency = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            try {
                insertUser(jdbc, encoder, owner, "@owner" + suffix, "owner-" + suffix + "@example.test", 1);
                insertUser(jdbc, encoder, contact, "@contact" + suffix, "contact-" + suffix + "@example.test", 2);
                insertUser(jdbc, encoder, outsider, "@outside" + suffix, "outside-" + suffix + "@example.test", 3);
                Instant now = Instant.now();
                jdbc.update("""
                        insert into contacts.emergency_contacts (contact_id, owner_user_id, contact_user_id, relationship_status,
                            status_changed_at, created_at, updated_at)
                        values (?, ?, ?, 'ACCEPTED', ?, ?, ?)
                        """, UUID.randomUUID(), owner, contact, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergencies (emergency_id, user_id, status, message_snapshot, started_at, created_at, updated_at)
                        values (?, ?, 'ACTIVE', 'Necesito ayuda', ?, ?, ?)
                        """, emergency, owner, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergency_status_history (emergency_status_history_id, emergency_id, sequence_no,
                            previous_status, new_status, actor_user_id, occurred_at)
                        values (?, ?, 1, null, 'ACTIVE', ?, ?)
                        """, UUID.randomUUID(), emergency, owner, Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergency_locations (location_id, emergency_id, latitude, longitude, captured_at, received_at)
                        values (?, ?, 4.609710, -74.081750, ?, ?)
                        """, UUID.randomUUID(), emergency, Timestamp.from(now), Timestamp.from(now));
                String reference = evidence + ".webp";
                Files.write(storage.resolve(reference), new byte[] {'R', 'I', 'F', 'F'});
                jdbc.update("""
                        insert into emergency.emergency_evidences (evidence_id, emergency_id, evidence_sequence, file_reference,
                            original_file_name, mime_type, original_mime_type, file_size_bytes, received_at)
                        values (?, ?, 1, ?, 'camera.webp', 'image/webp', 'image/webp', 4, ?)
                        """, evidence, emergency, reference, Timestamp.from(now));

                String contactToken = login(client, json, baseUrl, "contact-" + suffix + "@example.test");
                String outsiderToken = login(client, json, baseUrl, "outside-" + suffix + "@example.test");
                String ownerToken = login(client, json, baseUrl, "owner-" + suffix + "@example.test");
                HttpResponse<String> listed = call(client, "GET", baseUrl + "/me/contact-alerts?page=0&size=20", contactToken, null);
                assertStatus(listed, 200);
                assertThat(json.readTree(listed.body()).path("items").get(0).path("emergencyId").asString())
                        .isEqualTo(emergency.toString());

                HttpResponse<String> detail = call(client, "GET", baseUrl + "/me/contact-alerts/" + emergency, contactToken, null);
                assertStatus(detail, 200);
                JsonNode body = json.readTree(detail.body());
                assertThat(body.path("messageSnapshot").asString()).isEqualTo("Necesito ayuda");
                assertThat(body.has("email")).isFalse();
                assertThat(body.has("phone")).isFalse();
                assertThat(body.path("evidences").get(0).path("evidenceId").asString()).isEqualTo(evidence.toString());
                assertStatus(call(client, "GET", baseUrl + "/me/contact-alerts/" + emergency + "/evidences", contactToken, null), 200);
                HttpResponse<String> content = call(client, "GET", baseUrl + "/me/contact-alerts/" + emergency
                        + "/evidences/" + evidence + "/content", contactToken, null);
                assertStatus(content, 200);
                assertThat(content.headers().firstValue("Cache-Control")).contains("no-store");
                assertStatus(call(client, "GET", baseUrl + "/me/contact-alerts/" + emergency, outsiderToken, null), 404);

                assertStatus(call(client, "POST", baseUrl + "/emergencies/" + emergency + "/finish", ownerToken,
                        "{\"confirmedSafe\":true}"), 204);
                assertThat(json.readTree(call(client, "GET", baseUrl + "/me/contact-alerts", contactToken, null).body())
                        .path("items").size()).isZero();
                assertStatus(call(client, "GET", baseUrl + "/me/contact-alerts/" + emergency, contactToken, null), 404);
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?, ?)", owner, contact, outsider);
            }
        } finally {
            Files.deleteIfExists(storage.resolve(evidence + ".webp"));
            Files.deleteIfExists(storage);
        }
    }

    private static String login(HttpClient client, ObjectMapper json, String baseUrl, String email) throws Exception {
        HttpResponse<String> response = call(client, "POST", baseUrl + "/auth/login", null,
                "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        assertStatus(response, 200);
        return json.readTree(response.body()).path("accessToken").asString();
    }

    private static HttpResponse<String> call(HttpClient client, String method, String url, String token, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    }

    private static void insertUser(JdbcTemplate jdbc, PasswordEncoder encoder, UUID id, String username, String email, int number) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, id, username, email, "3" + String.format("%09d", number), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update("""
                insert into identity.user_credentials (credential_id, user_id, password_hash, password_updated_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, encoder.encode(PASSWORD), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
