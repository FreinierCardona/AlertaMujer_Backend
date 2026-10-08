package com.alertamujer.backend.evidence.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deletes only relative references contained by the configured private evidence directory. */
@Component
class LocalEvidenceFileCleanup implements EvidenceFileCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEvidenceFileCleanup.class);
    private final String storagePath;

    LocalEvidenceFileCleanup(@Value("${evidence.storage-path:}") String storagePath) { this.storagePath = storagePath; }

    @Override
    public void deleteAfterAccountRemoval(Collection<String> references) {
        if (references.isEmpty()) return;
        if (storagePath == null || storagePath.isBlank()) {
            LOGGER.warn("Evidence cleanup deferred: storage is not configured.");
            return;
        }
        Path root = Path.of(storagePath).toAbsolutePath().normalize();
        for (String reference : references) deleteWithOneRetry(root, reference);
    }

    private void deleteWithOneRetry(Path root, String reference) {
        if (reference == null || reference.isBlank()) return;
        Path candidate;
        try {
            candidate = root.resolve(reference).normalize();
        } catch (RuntimeException exception) {
            LOGGER.warn("Evidence cleanup deferred for an invalid internal reference.");
            return;
        }
        if (!candidate.startsWith(root)) {
            LOGGER.warn("Evidence cleanup deferred for an out-of-root reference.");
            return;
        }
        tryDelete(candidate);
        if (Files.exists(candidate)) tryDelete(candidate);
    }

    private void tryDelete(Path candidate) {
        try { Files.deleteIfExists(candidate); }
        catch (IOException exception) { LOGGER.warn("Evidence cleanup attempt failed; reconciliation will retry."); }
    }
}
