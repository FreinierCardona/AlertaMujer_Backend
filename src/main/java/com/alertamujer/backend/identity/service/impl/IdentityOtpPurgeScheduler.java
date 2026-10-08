package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the documented bounded temporary-data cleanup once per UTC hour. */
@Component
class IdentityOtpPurgeScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityOtpPurgeScheduler.class);

    private final OtpService otpService;

    IdentityOtpPurgeScheduler(OtpService otpService) {
        this.otpService = otpService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void purgeHourly() {
        try {
            otpService.purgeTemporaryIdentityData();
        } catch (RuntimeException exception) {
            LOGGER.warn("Temporary identity cleanup deferred; a later run will retry.");
        }
    }
}
