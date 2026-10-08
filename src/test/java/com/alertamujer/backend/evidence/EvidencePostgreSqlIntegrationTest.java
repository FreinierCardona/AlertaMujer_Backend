package com.alertamujer.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertamujer.AlertaMujerApplication;
import com.alertamujer.backend.evidence.service.EvidenceService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/** Exercises HU-API-015 against the migrated PostgreSQL table and application grants when credentials are supplied. */
class EvidencePostgreSqlIntegrationTest {
    @Test
    void persistsOnlyMetadataForAnOwnedOpenEmergency() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
                "Integration database credentials were not supplied");
        Path storage = Files.createTempDirectory(Path.of("target"), "evidence-postgres-");
        SpringApplication application = new SpringApplication(AlertaMujerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "test", "spring.datasource.url", url,
                "spring.datasource.username", username, "spring.datasource.password", password,
                "evidence.storage-path", storage.toString(), "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            EvidenceService service = context.getBean(EvidenceService.class);
            UUID owner = UUID.randomUUID();
            UUID emergency = UUID.randomUUID();
            String suffix = owner.toString().substring(0, 8);
            Instant now = Instant.now();
            try {
                jdbc.update("""
                        insert into identity.users (user_id, username, first_names, last_names, email, phone, role, account_status,
                          account_origin, accepted_terms_at, created_at, updated_at)
                        values (?, ?, 'Ana', 'Perez', ?, ?, 'USER', 'ENABLED', 'SELF_REGISTERED', ?, ?, ?)
                        """, owner, "@evidence" + suffix, "evidence-" + suffix + "@example.com",
                        "3" + String.format("%09d", Math.floorMod(owner.getLeastSignificantBits(), 1_000_000_000L)),
                        Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                jdbc.update("""
                        insert into emergency.emergencies (emergency_id, user_id, status, message_snapshot, started_at, created_at, updated_at)
                        values (?, ?, 'ACTIVE', 'Help', ?, ?, ?)
                        """, emergency, owner, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

                var response = service.upload(new AuthenticatedIdentity(owner, UUID.randomUUID(), "USER", false), emergency, image());
                String reference = jdbc.queryForObject("select file_reference from emergency.emergency_evidences where evidence_id = ?",
                        String.class, response.evidenceId());
                assertThat(response.mimeType()).isEqualTo("image/webp");
                assertThat(response.sizeBytes()).isBetween(1, 1_048_576);
                assertThat(reference).matches("[0-9a-f-]{36}\\.webp");
                assertThat(Files.exists(storage.resolve(reference))).isTrue();
                assertThat(jdbc.queryForObject("select count(*) from emergency.emergency_evidences where emergency_id = ?", Integer.class,
                        emergency)).isEqualTo(1);
            } finally {
                jdbc.update("delete from identity.users where user_id = ?", owner);
            }
        }
    }

    private MockMultipartFile image() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        image.setRGB(4, 4, Color.RED.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "camera.png", "image/png", output.toByteArray());
    }
}
