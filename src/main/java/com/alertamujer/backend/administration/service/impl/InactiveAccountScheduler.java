package com.alertamujer.backend.administration.service.impl;

import com.alertamujer.backend.administration.repository.AdministrationRepository;
import com.alertamujer.backend.administration.service.AdministrationService;
import java.time.Clock;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Applies the documented three-month inactivity rule without an administrative HTTP route. */
@Component
class InactiveAccountScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(InactiveAccountScheduler.class);
    private static final int BATCH_SIZE = 500;

    private final AdministrationRepository repository;
    private final AdministrationService administrationService;
    private final Clock clock;

    @Autowired
    InactiveAccountScheduler(AdministrationRepository repository, AdministrationService administrationService) {
        this(repository, administrationService, Clock.systemUTC());
    }

    InactiveAccountScheduler(AdministrationRepository repository, AdministrationService administrationService, Clock clock) {
        this.repository = repository;
        this.administrationService = administrationService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    void disableInactiveAccounts() {
        var cutoff = clock.instant().atZone(ZoneOffset.UTC).minusMonths(3).toInstant();
        for (var userId : repository.findInactiveEnabledUserIds(cutoff, BATCH_SIZE)) {
            try {
                administrationService.disableUserIfStillInactive(userId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Inactive-account processing deferred; a later run will retry.");
            }
        }
    }
}
