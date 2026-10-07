package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.service.OtpService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the documented bounded temporary-data cleanup once per UTC hour. */
@Component
class IdentityOtpPurgeScheduler {

    private final OtpService otpService;

    IdentityOtpPurgeScheduler(OtpService otpService) {
        this.otpService = otpService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void purgeHourly() {
        otpService.purgeTemporaryIdentityData();
    }
}
