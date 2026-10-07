package com.alertamujer.backend.identity.model;

/** Purposes constrained by the deployed PostgreSQL schema. */
public enum OtpPurpose {
    PASSWORD_RESET,
    PHONE_VERIFICATION,
    EMAIL_VERIFICATION,
    PROFILE_CONTACT_CHANGE
}
