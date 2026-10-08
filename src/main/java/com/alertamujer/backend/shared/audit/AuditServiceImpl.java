package com.alertamujer.backend.shared.audit;

import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Sanitizes the small whitelist of state values before they are persisted as JSON objects. */
@Service
class AuditServiceImpl implements AuditService {
    private static final int DESCRIPTION_LIMIT = 500;
    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    AuditServiceImpl(AuditRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    AuditServiceImpl(AuditRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(AuditEvent event) {
        validate(event);
        try {
            repository.append(event, json(event.previousState()), json(event.newState()), clock.instant());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist administrative audit event.", exception);
        }
    }

    private String json(Map<String, String> state) throws Exception {
        return state == null || state.isEmpty() ? null : objectMapper.writeValueAsString(state);
    }

    private void validate(AuditEvent event) {
        if (event == null || event.actorUserId() == null || event.entityId() == null
                || !isAction(event.action()) || !"SUCCESS".equals(event.result())
                || event.entityType() == null || event.entityType().isBlank()
                || event.description() == null || event.description().isBlank()
                || event.description().length() > DESCRIPTION_LIMIT) {
            throw new IllegalArgumentException("Invalid administrative audit event.");
        }
    }

    private boolean isAction(String action) {
        return "ADMIN_LOGIN".equals(action) || "ALERT_VIEWED".equals(action)
                || "USER_PROFILE_VIEWED".equals(action) || "ALERT_STATUS_CHANGED".equals(action)
                || "ACCOUNT_STATUS_CHANGED".equals(action);
    }
}
