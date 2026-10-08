package com.alertamujer.backend.administration.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-only operational command for RN-ADM-02B; it is intentionally not an HTTP route. */
@Component
@Profile("local")
class AdministratorReplacementRunner implements ApplicationRunner {
    private final AdministrationService administrationService;
    private final String targetUserId;

    AdministratorReplacementRunner(AdministrationService administrationService,
            @Value("${ADMIN_REPLACEMENT_TARGET_USER_ID:}") String targetUserId) {
        this.administrationService = administrationService; this.targetUserId = targetUserId;
    }

    @Override public void run(ApplicationArguments arguments) {
        if (targetUserId == null || targetUserId.isBlank()) return;
        try { administrationService.replaceAdministrator(UUID.fromString(targetUserId.trim())); }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ADMIN_REPLACEMENT_TARGET_USER_ID must be a UUID.", exception);
        }
    }
}
