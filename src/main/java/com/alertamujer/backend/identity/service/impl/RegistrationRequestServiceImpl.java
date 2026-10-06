package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.RegistrationRequestEntity;
import com.alertamujer.backend.identity.repository.RegistrationRequestRepository;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistrationRequestServiceImpl implements RegistrationRequestService {

    private final RegistrationRequestRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigurationValues configuration;
    private final Clock clock;

    @Autowired
    public RegistrationRequestServiceImpl(RegistrationRequestRepository repository,
            PasswordEncoder passwordEncoder, SystemConfigurationValues configuration) {
        this(repository, passwordEncoder, configuration, Clock.systemUTC());
    }

    RegistrationRequestServiceImpl(RegistrationRequestRepository repository,
            PasswordEncoder passwordEncoder, SystemConfigurationValues configuration, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegistrationRequestResponse startPublicRegistration(RegistrationRequestInput input) {
        return create(input.username(), input.firstNames(), input.lastNames(), input.email(), input.phone(), input.password(),
                AccountOrigin.SELF_REGISTERED, true);
    }

    @Override
    @Transactional
    public RegistrationRequestResponse startAdministrativeRegistration(AdminRegistrationRequestInput input) {
        return create(input.username(), input.firstNames(), input.lastNames(), input.email(), input.phone(), input.password(),
                AccountOrigin.ADMIN_CREATED, false);
    }

    private RegistrationRequestResponse create(String username, String firstNames, String lastNames,
            String email, String phone, String password, AccountOrigin origin, boolean termsAccepted) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = phone.trim();

        if (repository.existsIdentityConflict(normalizedUsername, normalizedEmail, normalizedPhone)) {
            throw new RuleViolationException();
        }

        Instant now = clock.instant();
        RegistrationRequestEntity request = new RegistrationRequestEntity(
                normalizedUsername,
                firstNames.trim(),
                lastNames.trim(),
                normalizedEmail,
                normalizedPhone,
                passwordEncoder.encode(password),
                now.plusSeconds(configuration.otpTtlMinutes() * 60L),
                now,
                origin,
                termsAccepted ? now : null);
        try {
            RegistrationRequestEntity saved = repository.saveAndFlush(request);
            return new RegistrationRequestResponse(saved.getRegistrationRequestId(), saved.getStatus());
        } catch (DataIntegrityViolationException exception) {
            // The partial unique indexes are the final concurrent-write guard.
            throw new RuleViolationException();
        }
    }
}
