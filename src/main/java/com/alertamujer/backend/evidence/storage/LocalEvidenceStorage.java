package com.alertamujer.backend.evidence.storage;

import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Converts supported camera image uploads to bounded WebP files below the private configured directory. */
@Component
class LocalEvidenceStorage implements EvidenceStorage {
    static final long MAX_INPUT_BYTES = 10L * 1_024 * 1_024;
    private static final long MAX_PIXELS = 24_000_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEvidenceStorage.class);
    private static final Set<String> ACCEPTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final String storagePath;
    private final int maxFinalBytes;

    @Autowired
    LocalEvidenceStorage(@Value("${evidence.storage-path:}") String storagePath, SystemConfigurationValues configuration) {
        this(storagePath, configuration.maxEvidenceSizeBytes());
    }

    LocalEvidenceStorage(String storagePath, int maxFinalBytes) {
        this.storagePath = storagePath;
        this.maxFinalBytes = maxFinalBytes;
    }

    @Override
    public StoredEvidenceFile store(MultipartFile source) throws IOException {
        validateSource(source);
        BufferedImage image;
        try (InputStream input = source.getInputStream()) {
            image = ImageIO.read(input);
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
            throw new RuleViolationException();
        }
        byte[] webp = encodeWithinLimit(image);
        Path root = configuredRoot();
        Files.createDirectories(root);
        String reference = UUID.randomUUID() + ".webp";
        Path temporary = root.resolve("." + UUID.randomUUID() + ".tmp");
        Path target = root.resolve(reference);
        try {
            Files.write(temporary, webp);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new StoredEvidenceFile(reference, webp.length);
    }

    @Override
    public InputStream open(String reference) throws IOException {
        return Files.newInputStream(resolveReference(reference));
    }

    @Override
    public void deleteAfterPersistenceFailure(String reference) {
        Path candidate;
        try {
            candidate = resolveReference(reference);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Evidence cleanup deferred for an invalid internal reference.");
            return;
        }
        deleteTwice(candidate, "Evidence cleanup attempt failed; reconciliation will retry.");
    }

    @Override
    public int deleteUnreferencedOlderThan(Collection<String> references, Instant cutoff) {
        Path root;
        try {
            root = configuredRoot();
        } catch (IOException exception) {
            LOGGER.warn("Evidence reconciliation deferred: storage is not configured.");
            return 0;
        }
        if (!Files.isDirectory(root)) return 0;
        Set<String> referenced = new HashSet<>(references);
        int removed = 0;
        try (var files = Files.list(root)) {
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path candidate = iterator.next();
                if (deleteIfOrphanedAndOld(candidate, referenced, cutoff)) removed++;
            }
        } catch (IOException exception) {
            LOGGER.warn("Evidence reconciliation scan failed; a later run will retry.");
        }
        return removed;
    }

    private boolean deleteIfOrphanedAndOld(Path candidate, Set<String> referenced, Instant cutoff) {
        try {
            String name = candidate.getFileName().toString();
            if (!name.endsWith(".webp") || referenced.contains(name) || !Files.isRegularFile(candidate)) return false;
            if (Files.getLastModifiedTime(candidate).toInstant().isAfter(cutoff)) return false;
            return deleteTwice(candidate, "Evidence reconciliation cleanup failed; a later run will retry.");
        } catch (IOException exception) {
            LOGGER.warn("Evidence reconciliation skipped one file; a later run will retry.");
            return false;
        }
    }

    private void validateSource(MultipartFile source) {
        if (source == null || source.isEmpty() || source.getSize() > MAX_INPUT_BYTES
                || source.getContentType() == null || !ACCEPTED_TYPES.contains(source.getContentType().toLowerCase())) {
            throw new RuleViolationException();
        }
    }

    private byte[] encodeWithinLimit(BufferedImage original) throws IOException {
        for (double scale : new double[] {1.0, 0.8, 0.64, 0.5, 0.4, 0.3, 0.2}) {
            BufferedImage image = scale == 1.0 ? original : resized(original, scale);
            for (float quality : new float[] {0.90f, 0.80f, 0.70f, 0.60f, 0.50f, 0.40f, 0.30f, 0.20f, 0.10f}) {
                byte[] encoded = writeWebp(image, quality);
                if (encoded.length <= maxFinalBytes) return encoded;
            }
        }
        throw new RuleViolationException();
    }

    private byte[] writeWebp(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) throw new IOException("WebP writer unavailable");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] compressionTypes = parameters.getCompressionTypes();
                if (compressionTypes != null) {
                    for (String compressionType : compressionTypes) {
                        if ("Lossy".equalsIgnoreCase(compressionType)) {
                            parameters.setCompressionType(compressionType);
                            break;
                        }
                    }
                }
                parameters.setCompressionQuality(quality);
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage resized(BufferedImage original, double scale) {
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private Path configuredRoot() throws IOException {
        if (storagePath == null || storagePath.isBlank()) throw new IOException("Evidence storage is not configured");
        return Path.of(storagePath).toAbsolutePath().normalize();
    }

    private Path resolveReference(String reference) throws IOException {
        if (reference == null || !reference.matches("[0-9a-fA-F-]{36}\\.webp")) throw new IOException("Invalid evidence reference");
        Path root = configuredRoot();
        Path candidate = root.resolve(reference).normalize();
        if (!candidate.startsWith(root)) throw new IOException("Out-of-root evidence reference");
        return candidate;
    }

    private boolean deleteTwice(Path candidate, String warning) {
        try {
            Files.deleteIfExists(candidate);
            if (!Files.exists(candidate)) return true;
            Files.deleteIfExists(candidate);
            return !Files.exists(candidate);
        } catch (IOException exception) {
            LOGGER.warn(warning);
            return false;
        }
    }
}
