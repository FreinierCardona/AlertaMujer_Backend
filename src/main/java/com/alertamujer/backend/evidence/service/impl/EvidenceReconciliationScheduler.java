package com.alertamujer.backend.evidence.service.impl;

import com.alertamujer.backend.evidence.service.EvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps physical evidence cleanup inside its owning module and outside public HTTP. */
@Component
class EvidenceReconciliationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceReconciliationScheduler.class);
    private final EvidenceService evidenceService;

    EvidenceReconciliationScheduler(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void reconcileOrphans() {
        try {
            evidenceService.reconcileOrphans();
        } catch (RuntimeException exception) {
            LOGGER.warn("Evidence reconciliation deferred; a later run will retry.");
        }
    }
}
