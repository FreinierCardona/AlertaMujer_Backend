package com.alertamujer.backend.evidence.storage;

import java.util.Collection;

/** Narrow bridge used by account deletion; upload and reconciliation remain evidence-module work. */
public interface EvidenceFileCleanup {
    void deleteAfterAccountRemoval(Collection<String> references);
}
