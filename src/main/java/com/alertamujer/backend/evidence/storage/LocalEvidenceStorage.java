package com.alertamujer.backend.evidence.storage;

import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Validates and normalizes transferred WebP evidence below the private configured directory. */
@Component
class LocalEvidenceStorage implements EvidenceStorage {
    static final int MAX_WEBP_BYTES = 500_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEvidenceStorage.class);
    private final String storagePath;
    private final int maxFinalBytes;

    @Autowired
    LocalEvidenceStorage(@Value("${evidence.storage-path:}") String storagePath, SystemConfigurationValues configuration) {
        this(storagePath, configuration.maxEvidenceSizeBytes());
    }

    LocalEvidenceStorage(String storagePath, int maxFinalBytes) {
        this.storagePath = storagePath;
        this.maxFinalBytes = Math.min(maxFinalBytes, MAX_WEBP_BYTES);
    }

    @Override
    public StoredEvidenceFile store(MultipartFile source) throws IOException {
        byte[] webp = source == null ? null : source.getBytes();
        validateSource(source, webp);
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

    private void validateSource(MultipartFile source, byte[] webp) {
        if (source == null || source.isEmpty() || webp == null || webp.length > maxFinalBytes
                || source.getContentType() == null || !"image/webp".equalsIgnoreCase(source.getContentType().trim())
                || !isWebpContainer(webp)) {
            throw new RuleViolationException();
        }
    }

    private boolean isWebpContainer(byte[] bytes) {
        if (bytes.length < 20 || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F'
                || bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') return false;
        long declaredRiffSize = Integer.toUnsignedLong((bytes[4] & 0xff)
                | ((bytes[5] & 0xff) << 8) | ((bytes[6] & 0xff) << 16) | ((bytes[7] & 0xff) << 24));
        if (declaredRiffSize != bytes.length - 8L) return false;
        boolean imageChunkFound = false;
        long offset = 12;
        while (offset + 8 <= bytes.length) {
            int start = (int) offset;
            byte[] chunkType = Arrays.copyOfRange(bytes, start, start + 4);
            long chunkSize = Integer.toUnsignedLong((bytes[start + 4] & 0xff)
                    | ((bytes[start + 5] & 0xff) << 8) | ((bytes[start + 6] & 0xff) << 16)
                    | ((bytes[start + 7] & 0xff) << 24));
            offset += 8 + chunkSize + (chunkSize & 1);
            if (offset > bytes.length) return false;
            imageChunkFound |= Arrays.equals(chunkType, new byte[] {'V', 'P', '8', ' '})
                    || Arrays.equals(chunkType, new byte[] {'V', 'P', '8', 'L'});
        }
        return imageChunkFound && offset == bytes.length;
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
