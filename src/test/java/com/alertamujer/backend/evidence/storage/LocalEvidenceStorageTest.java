package com.alertamujer.backend.evidence.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalEvidenceStorageTest {
    private Path directory;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory(Path.of("target"), "evidence-storage-");
    }

    @Test
    void convertsAnAcceptedImageToBoundedWebpWithoutExposingItsPath() throws Exception {
        LocalEvidenceStorage storage = storage();
        StoredEvidenceFile stored = storage.store(cameraImage());

        assertThat(stored.reference()).matches("[0-9a-f-]{36}\\.webp");
        assertThat(stored.sizeBytes()).isBetween(1, 1_048_576);
        assertThat(ImageIO.read(storage.open(stored.reference()))).isNotNull();
        assertThat(Files.exists(directory.resolve(stored.reference()))).isTrue();
    }

    @Test
    void rejectsArbitraryFilesBeforeCreatingAFile() {
        LocalEvidenceStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "not a photograph".getBytes());

        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void rejectsADeclaredPhotoThatCannotBeConverted() {
        LocalEvidenceStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile("file", "camera.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void rejectsInputOverTheConfiguredTransportLimit() {
        LocalEvidenceStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile("file", "camera.png", "image/png",
                new byte[(int) LocalEvidenceStorage.MAX_INPUT_BYTES + 1]);

        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void reconcilesOnlyOldUnreferencedWebpFiles() throws Exception {
        LocalEvidenceStorage storage = storage();
        Files.write(directory.resolve("11111111-1111-1111-1111-111111111111.webp"), new byte[] {1});
        Files.write(directory.resolve("22222222-2222-2222-2222-222222222222.webp"), new byte[] {1});
        Files.setLastModifiedTime(directory.resolve("11111111-1111-1111-1111-111111111111.webp"),
                FileTime.from(Instant.now().minusSeconds(25 * 60 * 60)));

        assertThat(storage.deleteUnreferencedOlderThan(java.util.List.of("22222222-2222-2222-2222-222222222222.webp"),
                Instant.now().minusSeconds(24 * 60 * 60))).isEqualTo(1);
        assertThat(Files.exists(directory.resolve("11111111-1111-1111-1111-111111111111.webp"))).isFalse();
        assertThat(Files.exists(directory.resolve("22222222-2222-2222-2222-222222222222.webp"))).isTrue();
    }

    private MockMultipartFile cameraImage() throws Exception {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
            image.setRGB(x, y, new Color(x % 255, y % 255, (x + y) % 255).getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", "camera.png", "image/png", bytes.toByteArray());
    }

    @Test
    void rejectsAWebpLargerThanTheConfiguredEvidenceLimit() throws Exception {
        LocalEvidenceStorage storage = new LocalEvidenceStorage(directory.toString(), 1);

        assertThatThrownBy(() -> storage.store(cameraImage())).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    private LocalEvidenceStorage storage() {
        return new LocalEvidenceStorage(directory.toString(), 1_048_576);
    }
}
