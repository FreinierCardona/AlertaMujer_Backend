package com.alertamujer.backend.notification.service.impl;

import com.alertamujer.backend.emergency.event.EmergencyCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts notification work only when the SOS transaction has already committed. */
@Component
class EmergencyNotificationListener {
    private final NotificationDispatchService dispatchService;

    EmergencyNotificationListener(NotificationDispatchService dispatchService) { this.dispatchService = dispatchService; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterEmergencyCommit(EmergencyCreatedEvent event) { dispatchService.dispatch(event); }
}
