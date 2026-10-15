package com.alertamujer.backend.evidence.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalEvidenceStorageTest {
    private Path directory;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory(Path.of("target"), "evidence-storage-");
    }

    @Test
    void storesAnAcceptedWebpWithinThe500KbLimitWithoutExposingItsPath() throws Exception {
        LocalEvidenceStorage storage = storage();
        StoredEvidenceFile stored = storage.store(webpImage());

        assertThat(stored.reference()).matches("[0-9a-f-]{36}\\.webp");
        assertThat(stored.sizeBytes()).isBetween(1, 500_000);
        try (InputStream storedBytes = storage.open(stored.reference())) {
            assertThat(storedBytes.readAllBytes()).isEqualTo(webpImageBytes());
        }
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
    void rejectsOneByteDeclaredWebpBeforeCreatingAFile() {
        LocalEvidenceStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile("file", "camera.webp", "image/webp", new byte[] {1});

        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void rejectsWebpOver500000BytesBeforeCreatingAFile() {
        LocalEvidenceStorage storage = storage();
        byte[] bytes = new byte[LocalEvidenceStorage.MAX_WEBP_BYTES + 1];
        bytes[0] = 'R'; bytes[1] = 'I'; bytes[2] = 'F'; bytes[3] = 'F';
        bytes[8] = 'W'; bytes[9] = 'E'; bytes[10] = 'B'; bytes[11] = 'P';
        MockMultipartFile file = new MockMultipartFile("file", "camera.webp", "image/webp", bytes);

        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void acceptsWebpAtExactly500000Bytes() throws Exception {
        LocalEvidenceStorage storage = storage();
        StoredEvidenceFile stored = storage.store(new MockMultipartFile("file", "camera.webp", "image/webp", paddedWebpAtLimit()));

        assertThat(stored.sizeBytes()).isEqualTo(LocalEvidenceStorage.MAX_WEBP_BYTES);
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

    private MockMultipartFile webpImage() throws Exception {
        return new MockMultipartFile("file", "camera.webp", "image/webp", webpImageBytes());
    }

    private byte[] webpImageBytes() throws Exception {
        return Base64.getDecoder().decode("UklGRkAAAABXRUJQVlA4IDQAAADwAQCdASoQABAAAMASJZwC7DBAAZB+E0AA/v/+TifOgrR8c9327nURIAASGk+ySVVmAAAA");
    }

    private byte[] paddedWebpAtLimit() throws Exception {
        byte[] source = webpImageBytes();
        byte[] result = java.util.Arrays.copyOf(source, LocalEvidenceStorage.MAX_WEBP_BYTES);
        int junkOffset = source.length;
        result[junkOffset] = 'J'; result[junkOffset + 1] = 'U'; result[junkOffset + 2] = 'N'; result[junkOffset + 3] = 'K';
        int junkSize = result.length - junkOffset - 8;
        result[junkOffset + 4] = (byte) junkSize;
        result[junkOffset + 5] = (byte) (junkSize >>> 8);
        result[junkOffset + 6] = (byte) (junkSize >>> 16);
        result[junkOffset + 7] = (byte) (junkSize >>> 24);
        int riffSize = result.length - 8;
        result[4] = (byte) riffSize;
        result[5] = (byte) (riffSize >>> 8);
        result[6] = (byte) (riffSize >>> 16);
        result[7] = (byte) (riffSize >>> 24);
        return result;
    }

    @Test
    void rejectsAWebpLargerThanTheConfiguredEvidenceLimit() throws Exception {
        LocalEvidenceStorage storage = new LocalEvidenceStorage(directory.toString(), 1);

        assertThatThrownBy(() -> storage.store(webpImage())).isInstanceOf(RuleViolationException.class);
        assertThat(directory).isEmptyDirectory();
    }

    private LocalEvidenceStorage storage() {
        return new LocalEvidenceStorage(directory.toString(), 500_000);
    }
}
