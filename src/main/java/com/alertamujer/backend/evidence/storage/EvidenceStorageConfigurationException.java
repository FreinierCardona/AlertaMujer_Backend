package com.alertamujer.backend.evidence.storage;

/** Indica una configuración local de almacenamiento ausente o no escribible. */
public final class EvidenceStorageConfigurationException extends RuntimeException {
    public EvidenceStorageConfigurationException(String message) {
        super(message);
    }

    public EvidenceStorageConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
