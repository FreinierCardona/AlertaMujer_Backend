package com.alertamujer.backend.contacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/** End-to-end HTTP proof for the Mobile contacts and device-token contract. */
class ContactHttpPostgreSqlIntegrationTest {

    private static final String PASSWORD = "SecurePass#2026";

    @Test
    void exposesSafeRelationshipsAndExecutesTheContactAndDeviceTokenFlow() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "server.port", 0,
                "management.health.mail.enabled", false, "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper json = context.getBean(ObjectMapper.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            HttpClient client = HttpClient.newHttpClient();
            String baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            UUID owner = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String ownerUsername = "@owner" + suffix;
            String recipientUsername = "@recipient" + suffix;
            String deviceToken = "fcm-http-contact-" + UUID.randomUUID();
            try {
                insertUser(jdbc, encoder, owner, ownerUsername, "Owner", suffix, 1);
                insertUser(jdbc, encoder, recipient, recipientUsername, "Recipient", suffix, 2);
                String ownerToken = login(client, json, baseUrl, "contact-1-" + suffix + "@example.com");
                String recipientToken = login(client, json, baseUrl, "contact-2-" + suffix + "@example.com");

                HttpResponse<String> directory = call(client, "GET", baseUrl + "/api/v1/directory?query=recipient&page=0&size=20",
                        ownerToken, null);
                assertStatus(directory, 200);
                JsonNode directoryItem = json.readTree(directory.body()).path("items").get(0);
                assertThat(directoryItem.path("username").asString()).isEqualTo(recipientUsername);
                assertThat(directoryItem.has("email")).isFalse();
                assertThat(directoryItem.has("phone")).isFalse();

                HttpResponse<String> invitation = call(client, "POST", baseUrl + "/api/v1/contact-invitations", ownerToken,
                        "{\"username\":\"" + recipientUsername + "\"}");
                assertStatus(invitation, 201);
                String contactId = json.readTree(invitation.body()).path("contactId").asString();

                JsonNode sent = onlyContact(json, call(client, "GET", baseUrl + "/api/v1/contacts?page=0&size=20", ownerToken, null));
                assertThat(sent.path("counterpart").path("username").asString()).isEqualTo(recipientUsername);
                assertThat(sent.path("direction").asString()).isEqualTo("SENT");
                assertThat(sent.path("allowedActions").size()).isZero();
                assertThat(sent.path("counterpart").has("email")).isFalse();
                assertThat(sent.path("counterpart").has("phone")).isFalse();

                JsonNode received = onlyContact(json,
                        call(client, "GET", baseUrl + "/api/v1/contacts?page=0&size=20", recipientToken, null));
                assertThat(received.path("direction").asString()).isEqualTo("RECEIVED");
                assertThat(received.path("allowedActions").toString()).contains("ACCEPT", "REJECT");
                assertStatus(call(client, "POST", baseUrl + "/api/v1/contact-invitations/" + contactId + "/accept", ownerToken, null), 403);
                assertStatus(call(client, "POST", baseUrl + "/api/v1/contact-invitations/" + contactId + "/accept", recipientToken, null), 204);

                JsonNode accepted = onlyContact(json,
                        call(client, "GET", baseUrl + "/api/v1/contacts?page=0&size=20", ownerToken, null));
                assertThat(accepted.path("status").asString()).isEqualTo("ACCEPTED");
                assertThat(accepted.path("eligible").asBoolean()).isTrue();

                String tokenBody = "{\"token\":\"" + deviceToken + "\",\"platform\":\"ANDROID\"}";
                assertStatus(call(client, "POST", baseUrl + "/api/v1/me/device-tokens", ownerToken, tokenBody), 201);
                assertStatus(call(client, "POST", baseUrl + "/api/v1/me/device-tokens", ownerToken, tokenBody), 200);
                assertStatus(call(client, "POST", baseUrl + "/api/v1/me/device-tokens", recipientToken, tokenBody), 409);
            } finally {
                jdbc.update("delete from identity.users where user_id in (?, ?)", owner, recipient);
            }
        }
    }

    private static JsonNode onlyContact(ObjectMapper json, HttpResponse<String> response) throws Exception {
        assertStatus(response, 200);
        return json.readTree(response.body()).path("items").get(0);
    }

    private static String login(HttpClient client, ObjectMapper json, String baseUrl, String email) throws Exception {
        HttpResponse<String> response = call(client, "POST", baseUrl + "/api/v1/auth/login", null,
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

    private static void insertUser(JdbcTemplate jdbc, PasswordEncoder encoder, UUID id, String username, String firstName,
            String suffix, int number) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, created_at, updated_at)
                values (?, ?, ?, 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                """, id, username, firstName, "contact-" + number + "-" + suffix + "@example.com",
                "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into identity.user_credentials (credential_id, user_id, password_hash, password_updated_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, encoder.encode(PASSWORD), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
