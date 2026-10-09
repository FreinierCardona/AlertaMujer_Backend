package com.alertamujer.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.chat.dto.request.ChatMessageInput;
import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.shared.errors.StateConflictException;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises HU-API-016 with the deployed PostgreSQL table and application-table grants when credentials are supplied. */
class ChatPostgreSqlIntegrationTest {
    @Test
    void persistsOnceOrdersByServerIdAndAllowsRecoveryAfterFinalization() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password,
                "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ChatService service = context.getBean(ChatService.class);
            UUID owner = UUID.randomUUID();
            UUID emergency = UUID.randomUUID();
            UUID firstClientMessageId = UUID.randomUUID();
            String suffix = owner.toString().substring(0, 8);
            Instant now = Instant.now();
            try {
                jdbc.update("""
                        insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                          account_origin, accepted_terms_at, created_at, updated_at)
                        values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                        """, owner, "@chat" + suffix, "chat-" + suffix + "@example.com",
                        "3" + String.format("%09d", Math.floorMod(owner.getLeastSignificantBits(), 1_000_000_000L)),
                        Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergencies (emergency_id, user_id, status, message_snapshot, started_at, created_at, updated_at)
                        values (?, ?, 'ACTIVE', 'Help', ?, ?, ?)
                        """, emergency, owner, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                AuthenticatedIdentity identity = new AuthenticatedIdentity(owner, UUID.randomUUID(), "USER", false);

                var first = service.send(identity, emergency, new ChatMessageInput(firstClientMessageId, "  first  "));
                var repeated = service.send(identity, emergency, new ChatMessageInput(firstClientMessageId, "ignored retry"));
                var second = service.send(identity, emergency, new ChatMessageInput(UUID.randomUUID(), "second"));

                assertThat(repeated.messageId()).isEqualTo(first.messageId());
                assertThat(first.senderUserId()).isEqualTo(owner);
                assertThat(first.senderRole()).isEqualTo("USER");
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_chat_messages where emergency_id = ?",
                        Integer.class, emergency)).isEqualTo(2);
                assertThat(service.list(identity, emergency, first.messageId(), 20)).extracting(message -> message.messageId())
                        .containsExactly(second.messageId());

                jdbc.update("update emergency.emergencies set status = 'FINALIZED', finalized_at = ?, updated_at = ? where emergency_id = ?",
                        Timestamp.from(now), Timestamp.from(now), emergency);
                assertThat(service.list(identity, emergency, 0, 20)).hasSize(2);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(identity, emergency,
                        new ChatMessageInput(UUID.randomUUID(), "not accepted"))).isInstanceOf(StateConflictException.class);
            } finally {
                jdbc.update("delete from identity.users where user_id = ?", owner);
            }
        }
    }
}
