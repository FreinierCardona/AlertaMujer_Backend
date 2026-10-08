package com.alertamujer.backend.shared.audit;

/** Appends only the administrative events allowed by the database constraint. */
public interface AuditService {
    void record(AuditEvent event);

    static AuditService noop() {
        return event -> { };
    }
}
