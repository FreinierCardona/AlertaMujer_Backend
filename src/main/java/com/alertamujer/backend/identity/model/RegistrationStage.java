package com.alertamujer.backend.identity.model;

/** Ordered temporary stages for the public v2 phone-first registration flow. */
public enum RegistrationStage {
    PHONE_PENDING,
    PHONE_VERIFIED,
    EMAIL_PENDING,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
