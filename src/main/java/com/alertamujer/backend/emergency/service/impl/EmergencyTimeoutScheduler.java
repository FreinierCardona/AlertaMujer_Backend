package com.alertamujer.backend.emergency.service.impl;

import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the SOS timeout in small batches; the service owns the state transition. */
@Component
class EmergencyTimeoutScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmergencyTimeoutScheduler.class);
    private static final int BATCH_SIZE = 500;

    private final EmergencyRepository repository;
    private final EmergencyService emergencyService;
    private final SystemConfigurationValues configuration;
    private final Clock clock;

    @Autowired
    EmergencyTimeoutScheduler(EmergencyRepository repository, EmergencyService emergencyService,
            SystemConfigurationValues configuration) {
        this(repository, emergencyService, configuration, Clock.systemUTC());
    }

    EmergencyTimeoutScheduler(EmergencyRepository repository, EmergencyService emergencyService,
            SystemConfigurationValues configuration, Clock clock) {
        this.repository = repository;
        this.emergencyService = emergencyService;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    void markTimedOutEmergenciesOffline() {
        Instant cutoff = clock.instant().minusSeconds(configuration.offlineTimeoutSeconds());
        for (var emergencyId : repository.findTimedOutEmergencyIds(cutoff, BATCH_SIZE)) {
            try {
                emergencyService.markOfflineIfTimedOut(emergencyId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Emergency timeout processing deferred; a later run will retry.");
            }
        }
    }
}
