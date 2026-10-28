package com.alertamujer.backend.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/** Prueba HTTP de extremo a extremo para cada ruta pública a cargo de HU-API-017. */
class AdministrationHttpPostgreSqlIntegrationTest {
    private static final String PASSWORD = "SecurePass#2026";

    @Test
    void operatesEveryAdministrativeRouteWithRealPostgreSqlGrantsAndAudits(@TempDir Path evidenceDirectory) throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");

        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password, "server.port", 0,
                "management.health.mail.enabled", false, "spring.main.banner-mode", "off",
                "evidence.storage-path", evidenceDirectory.toString()));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Assumptions.assumeTrue(jdbc.queryForObject("select count(*) from identity.users where role = 'ENTITY_ADMIN'",
                    Integer.class) == 0, "A configured administrator is preserved; this isolated fixture is skipped");

            ObjectMapper json = context.getBean(ObjectMapper.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            EmergencyService emergencyService = context.getBean(EmergencyService.class);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UUID administrator = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID contact = UUID.randomUUID();
            UUID statusTarget = UUID.randomUUID();
            UUID deletionTarget = UUID.randomUUID();
            String registrationUsername = "@created" + suffix;
            HttpClient client = HttpClient.newHttpClient();
            String baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            try {
                Instant now = Instant.now();
                insertUser(jdbc, administrator, "@admin" + suffix, suffix, 1, "ENTITY_ADMIN", "ENABLED", now, encoder);
                insertUser(jdbc, owner, "@owner" + suffix, suffix, 2, "USER", "ENABLED", now, encoder);
                insertUser(jdbc, contact, "@contact" + suffix, suffix, 3, "USER", "ENABLED", now, null);
                insertUser(jdbc, statusTarget, "@status" + suffix, suffix, 4, "USER", "ENABLED",
                        now.atZone(ZoneOffset.UTC).minusMonths(2).minusSeconds(1).toInstant(), null);
                insertUser(jdbc, deletionTarget, "@delete" + suffix, suffix, 5, "USER", "DISABLED",
                        now.atZone(ZoneOffset.UTC).minusDays(16).toInstant(), null);
                insertAcceptedContact(jdbc, owner, contact);
                UUID emergencyId = emergencyService.createOrRecover(identity(owner), new EmergencyCreateInput(
                        new BigDecimal("4.609710"), new BigDecimal("-74.081750"), new BigDecimal("8.5"),
                        now.minusSeconds(2), null)).emergency().emergencyId();
                jdbc.update("""
                        insert into emergency.emergency_chat_messages (client_message_id, emergency_id, sender_user_id, content, sent_at)
                        values (?, ?, ?, 'Administrative audit fixture', ?)
                        """, UUID.randomUUID(), emergencyId, owner, Timestamp.from(now));

                String adminToken = login(client, json, baseUrl, "admin-1-" + suffix + "@example.com");
                String userToken = login(client, json, baseUrl, "admin-2-" + suffix + "@example.com");
                byte[] photo = java.util.Base64.getDecoder().decode("UklGRkAAAABXRUJQVlA4IDQAAADwAQCdASoQABAAAMASJZwC7DBAAZB+E0AA/v/+TifOgrR8c9327nURIAASGk+ySVVmAAAA");
                String boundary = "stage-one-photo";
                java.io.ByteArrayOutputStream multipart = new java.io.ByteArrayOutputStream();
                multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"camera.webp\"\r\nContent-Type: image/webp\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                multipart.write(photo);
                multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                HttpResponse<String> uploaded = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/emergencies/" + emergencyId + "/evidences"))
                        .header("Authorization", "Bearer " + userToken).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
                assertStatus(uploaded, 201);
                String evidenceId = json.readTree(uploaded.body()).path("evidenceId").asText();
                assertStatus(call(client, "GET", baseUrl + "/api/v1/admin/dashboard", userToken, null), 403);

                assertStatus(call(client, "GET", baseUrl + "/api/v1/admin/dashboard", adminToken, null), 200);
                HttpResponse<String> listing = call(client, "GET", baseUrl + "/api/v1/admin/emergencies", adminToken, null);
                assertStatus(listing, 200);
                assertThat(json.readTree(listing.body()).path("items").get(0).path("username").asText()).isEqualTo("@owner" + suffix);
                assertStatus(call(client, "GET", baseUrl + "/api/v1/admin/emergencies?status=ACTIVE", adminToken, null), 200);
                HttpResponse<String> detail = call(client, "GET", baseUrl + "/api/v1/emergencies/" + emergencyId, adminToken, null);
                assertStatus(detail, 200);
                assertThat(json.readTree(detail.body()).path("owner").path("username").asText()).isEqualTo("@owner" + suffix);
                assertThat(json.readTree(detail.body()).path("owner").path("email").asText()).isEqualTo("admin-2-" + suffix + "@example.com");
                assertStatus(call(client, "GET", baseUrl + "/api/v1/emergencies/" + emergencyId + "/evidences", adminToken, null), 200);
                HttpResponse<byte[]> downloaded = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/evidences/" + evidenceId + "/content"))
                        .header("Authorization", "Bearer " + adminToken).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(downloaded.statusCode()).isEqualTo(200);
                assertThat(downloaded.body()).isEqualTo(photo);
                assertStatus(call(client, "GET", baseUrl + "/api/v1/emergencies/" + emergencyId + "/messages?after=0&size=20", adminToken, null), 200);
                verifyStompExchange(client, json, baseUrl, emergencyId, adminToken, userToken);
                assertThat(jdbc.queryForObject("select status from emergency.emergencies where emergency_id = ?", String.class, emergencyId)).isEqualTo("IN_PROGRESS");
                assertStatus(call(client, "POST", baseUrl + "/api/v1/admin/emergencies/" + emergencyId + "/attention", adminToken, null), 204);
                assertStatus(call(client, "POST", baseUrl + "/api/v1/admin/emergencies/" + emergencyId + "/attention", adminToken, null), 204);
                assertStatus(call(client, "GET", baseUrl + "/api/v1/admin/users", adminToken, null), 200);
                assertStatus(call(client, "GET", baseUrl + "/api/v1/admin/users/" + owner, adminToken, null), 200);
                assertStatus(call(client, "PATCH", baseUrl + "/api/v1/admin/users/" + statusTarget + "/status", adminToken,
                        "{\"status\":\"DISABLED\"}"), 200);
                assertStatus(call(client, "DELETE", baseUrl + "/api/v1/admin/users/" + deletionTarget + "/deletion", adminToken, null), 204);
                assertStatus(call(client, "POST", baseUrl + "/api/v1/admin/users", adminToken, """
                        {"username":"%s","firstNames":"Created","lastNames":"User","email":"created-%s@example.com",
                         "phone":"3001234567","password":"SecurePass#2026"}
                        """.formatted(registrationUsername, suffix)), 201);
                HttpResponse<String> createdLogin = call(client, "POST", baseUrl + "/api/v1/auth/login", null,
                        """
                        {"identifier":"%s","password":"SecurePass#2026"}
                        """.formatted(registrationUsername));
                assertStatus(createdLogin, 200);
                assertThat(json.readTree(createdLogin.body()).path("accessToken").asString()).isNotBlank();
                assertThat(json.readTree(createdLogin.body()).path("user").path("accountStatus").asString()).isEqualTo("ENABLED");
                assertThat(json.readTree(createdLogin.body()).path("termsPending").asBoolean()).isTrue();
                HttpResponse<String> auditResponse = call(client, "GET", baseUrl + "/api/v1/admin/audit-logs", adminToken, null);
                assertStatus(auditResponse, 200);
                assertThat(json.readTree(auditResponse.body()).path("items").size()).isGreaterThanOrEqualTo(6);
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and action = 'ADMIN_LOGIN'",
                        Integer.class, administrator)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and action = 'ALERT_VIEWED'",
                        Integer.class, administrator)).isEqualTo(7);
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and action = 'ALERT_STATUS_CHANGED'",
                        Integer.class, administrator)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and action = 'USER_PROFILE_VIEWED'",
                        Integer.class, administrator)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where actor_user_id = ? and action = 'ACCOUNT_STATUS_CHANGED'",
                        Integer.class, administrator)).isEqualTo(1);
                HttpResponse<String> beforeFinish = call(client, "GET", baseUrl + "/api/v1/admin/dashboard", adminToken, null);
                assertStatus(beforeFinish, 200);
                long finalizedBefore = json.readTree(beforeFinish.body()).path("finalizedCount").asLong();
                assertThat(finalizedBefore).isEqualTo(jdbc.queryForObject(
                        "select count(*) from emergency.emergencies where status = 'FINALIZED'", Long.class));
                assertStatus(call(client, "POST", baseUrl + "/api/v1/emergencies/" + emergencyId + "/finish", userToken, "{\"confirmedSafe\":true}"), 204);
                HttpResponse<String> afterFinish = call(client, "GET", baseUrl + "/api/v1/admin/dashboard", adminToken, null);
                assertStatus(afterFinish, 200);
                assertThat(json.readTree(afterFinish.body()).path("finalizedCount").asLong()).isEqualTo(finalizedBefore + 1);
                HttpResponse<String> finalizedList = call(client, "GET", baseUrl + "/api/v1/admin/emergencies?status=FINALIZED&page=0&size=20", adminToken, null);
                assertStatus(finalizedList, 200);
                assertThat(json.readTree(finalizedList.body()).path("total").asLong()).isEqualTo(finalizedBefore + 1);
            } finally {
                jdbc.update("delete from identity.users where username = ?", registrationUsername);
                jdbc.update("delete from identity.registration_requests where username = ?", registrationUsername);
                jdbc.update("delete from identity.users where user_id in (?, ?, ?, ?, ?)", administrator, owner, contact,
                        statusTarget, deletionTarget);
            }
        }
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

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username, String suffix, int number, String role,
            String accountStatus, Instant activityAt, PasswordEncoder encoder) {
        Instant now = Instant.now();
        Timestamp disabledAt = "DISABLED".equals(accountStatus) ? Timestamp.from(activityAt) : null;
        jdbc.update("""
                insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                    account_origin, accepted_terms_at, last_activity_at, disabled_at, created_at, updated_at)
                values (?, ?, 'Ana', 'Perez', ?, ?, ?, ?, 'SELF_REGISTERED', ?, ?, ?, ?, ?)
                """, id, username, "admin-" + number + "-" + suffix + "@example.com",
                "3" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)), role, accountStatus,
                Timestamp.from(now), Timestamp.from(activityAt), disabledAt, Timestamp.from(now), Timestamp.from(now));
        if (encoder != null) {
            jdbc.update("""
                    insert into identity.user_credentials (credential_id, user_id, password_hash, password_updated_at, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, encoder.encode(PASSWORD), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        }
    }

    private static void verifyStompExchange(HttpClient client, ObjectMapper json, String baseUrl, UUID emergencyId,
            String adminToken, String userToken) throws Exception {
        StompInbox admin = new StompInbox();
        StompInbox user = new StompInbox();
        java.net.http.WebSocket adminSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(baseUrl.replace("http://", "ws://") + "/ws"), admin).join();
        java.net.http.WebSocket userSocket = null;
        try {
            adminSocket.sendText("CONNECT\naccept-version:1.2\nAuthorization:Bearer " + adminToken + "\n\n\0", true).join();
            admin.await("CONNECTED");
            adminSocket.sendText("SUBSCRIBE\nid:admin-test\ndestination:/topic/emergencies/" + emergencyId + "\n\n\0", true).join();
            userSocket = client.newWebSocketBuilder().buildAsync(URI.create(baseUrl.replace("http://", "ws://") + "/ws"), user).join();
            userSocket.sendText("CONNECT\naccept-version:1.2\nAuthorization:Bearer " + userToken + "\n\n\0", true).join();
            user.await("CONNECTED");
            String clientId = UUID.randomUUID().toString();
            String body = json.writeValueAsString(Map.of("clientMessageId", clientId, "content", "Stage one user message"));
            userSocket.sendText("SEND\ndestination:/app/emergencies/" + emergencyId + "/messages\ncontent-type:application/json\n\n" + body + "\0", true).join();
            String received = admin.await(clientId);
            assertThat(received).contains("EMERGENCY_MESSAGE_CREATED", "Stage one user message");
            userSocket.sendText("SUBSCRIBE\nid:user-test\ndestination:/topic/emergencies/" + emergencyId + "\n\n\0", true).join();
            // El evento recibido confirma la suscripción del administrador antes de responder.
            String replyId = UUID.randomUUID().toString();
            adminSocket.sendText("SEND\ndestination:/app/emergencies/" + emergencyId + "/messages\ncontent-type:application/json\n\n"
                    + json.writeValueAsString(Map.of("clientMessageId", replyId, "content", "Stage one administrator reply")) + "\0", true).join();
            assertThat(user.await("EMERGENCY_STATUS_CHANGED")).contains("IN_PROGRESS");
            assertThat(user.await(replyId)).contains("ENTITY_ADMIN", "Stage one administrator reply");
        } finally {
            adminSocket.abort(); if (userSocket != null) userSocket.abort();
        }
    }

    private static class StompInbox implements java.net.http.WebSocket.Listener {
        private final java.util.concurrent.BlockingQueue<String> frames = new java.util.concurrent.LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(java.net.http.WebSocket socket) { socket.request(1); }
        @Override public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) { frames.add(buffer.toString()); buffer.setLength(0); }
            socket.request(1); return null;
        }
        String await(String expected) throws InterruptedException {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < deadline) {
                String frame = frames.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (frame != null && frame.contains(expected)) return frame;
                if (frame != null && frame.startsWith("ERROR")) throw new AssertionError("STOMP rejected the authenticated operation");
            }
            throw new AssertionError("Expected STOMP event was not received");
        }
    }

    private static void insertAcceptedContact(JdbcTemplate jdbc, UUID owner, UUID contact) {
        Instant now = Instant.now();
        jdbc.update("""
                insert into contacts.emergency_contacts (contact_id, owner_user_id, contact_user_id, relationship_status,
                    status_changed_at, created_at, updated_at)
                values (?, ?, ?, 'ACCEPTED', ?, ?, ?)
                """, UUID.randomUUID(), owner, contact, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    private static AuthenticatedIdentity identity(UUID userId) {
        return new AuthenticatedIdentity(userId, UUID.randomUUID(), "USER", false);
    }
}
