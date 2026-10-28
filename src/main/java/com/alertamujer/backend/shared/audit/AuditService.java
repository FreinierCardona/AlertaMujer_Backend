package com.alertamujer.backend.shared.audit;

/** Agrega únicamente los eventos administrativos permitidos por la restricción de base de datos. */
public interface AuditService {
    void record(AuditEvent event);

    static AuditService noop() {
        return event -> { };
    }
}
