package com.alertamujer.backend.identity.model;

/** Etapas temporales ordenadas del flujo público v2 de registro que comienza por el teléfono. */
public enum RegistrationStage {
    PHONE_PENDING,
    PHONE_VERIFIED,
    EMAIL_PENDING,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
