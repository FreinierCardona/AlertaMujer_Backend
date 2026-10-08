package com.alertamujer.backend.evidence.service.impl;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.alertamujer.backend.evidence.service.EvidenceService;
import org.junit.jupiter.api.Test;

class EvidenceReconciliationSchedulerTest {
    @Test
    void swallowsAStorageFailureSoTheNextScheduledRunCanReconcileIt() {
        EvidenceService service = mock(EvidenceService.class);
        doThrow(new IllegalStateException()).when(service).reconcileOrphans();

        new EvidenceReconciliationScheduler(service).reconcileOrphans();

        verify(service).reconcileOrphans();
    }
}
