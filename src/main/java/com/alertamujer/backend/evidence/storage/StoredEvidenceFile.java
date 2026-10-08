package com.alertamujer.backend.evidence.storage;

/** Internal result of a successful local WebP conversion and move. */
public record StoredEvidenceFile(String reference, int sizeBytes) { }
