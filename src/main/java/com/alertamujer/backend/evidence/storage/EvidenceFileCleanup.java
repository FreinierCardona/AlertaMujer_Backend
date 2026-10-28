package com.alertamujer.backend.evidence.storage;

import java.util.Collection;

/**
 * Interfaz específica utilizada al eliminar cuentas; la carga y conciliación siguen a cargo del módulo
 * de evidencias.
 */
public interface EvidenceFileCleanup {
    void deleteAfterAccountRemoval(Collection<String> references);
}
