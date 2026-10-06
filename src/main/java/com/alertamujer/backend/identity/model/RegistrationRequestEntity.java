package com.alertamujer.backend.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Temporary identity data held until HU-API-008 completes both verifications.
 * This entity deliberately has no relation to users or credentials.
 */
@Entity
@Table(name = "registration_requests", schema = "identity")
public class RegistrationRequestEntity implements Persistable<UUID> {

    @Id
    @Column(name = "registration_request_id", nullable = false, updatable = false)
    private UUID registrationRequestId = UUID.randomUUID();

    @Column(nullable = false, length = 20, columnDefinition = "citext")
    private String username;

    @Column(name = "first_names", nullable = false, length = 100)
    private String firstNames;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_origin", nullable = false, length = 20)
    private AccountOrigin accountOrigin;

    @Column(name = "accepted_terms_at")
    private Instant acceptedTermsAt;

    @Transient
    private boolean newRequest = true;

    protected RegistrationRequestEntity() {
    }

    public RegistrationRequestEntity(String username, String firstNames, String lastNames,
            String email, String phone, String passwordHash, Instant expiresAt,
            Instant createdAt, AccountOrigin accountOrigin, Instant acceptedTermsAt) {
        this.username = username;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = "PENDING";
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.accountOrigin = accountOrigin;
        this.acceptedTermsAt = acceptedTermsAt;
    }

    public UUID getRegistrationRequestId() {
        return registrationRequestId;
    }

    @Override
    public UUID getId() {
        return registrationRequestId;
    }

    @Override
    public boolean isNew() {
        return newRequest;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        newRequest = false;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AccountOrigin getAccountOrigin() {
        return accountOrigin;
    }

    public Instant getAcceptedTermsAt() {
        return acceptedTermsAt;
    }
}
