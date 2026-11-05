package com.alertamujer.backend.identity.model;

/** Propósitos limitados por el esquema PostgreSQL desplegado. */
public enum OtpPurpose {
    PASSWORD_RESET,
    PHONE_VERIFICATION,
    EMAIL_VERIFICATION,
    PROFILE_CONTACT_CHANGE
}
