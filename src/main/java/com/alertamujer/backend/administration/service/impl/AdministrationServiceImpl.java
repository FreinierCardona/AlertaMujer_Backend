package com.alertamujer.backend.administration.service.impl;

import com.alertamujer.backend.administration.dto.response.AuditLogResponse;
import com.alertamujer.backend.administration.dto.response.DashboardResponse;
import com.alertamujer.backend.administration.repository.AdministrationRepository;
import com.alertamujer.backend.administration.repository.AdministrationRepository.ManagedUserData;
import com.alertamujer.backend.administration.repository.AdministrationRepository.ReplacementUserData;
import com.alertamujer.backend.administration.service.AdministrationService;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.repository.ProfileRepository.UserProfileData;
import com.alertamujer.backend.identity.service.ProfileService;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import com.alertamujer.backend.shared.audit.AuditEvent;
import com.alertamujer.backend.shared.audit.AuditRepository;
import com.alertamujer.backend.shared.audit.AuditService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates permitted panel operations without duplicating SOS or identity lifecycle rules. */
@Service
class AdministrationServiceImpl implements AdministrationService {
    private final AdministrationRepository repository;
    private final EmergencyService emergencyService;
    private final ProfileService profileService;
    private final RegistrationRequestService registrationRequestService;
    private final AuditRepository auditRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    AdministrationServiceImpl(AdministrationRepository repository, EmergencyService emergencyService,
            ProfileService profileService, RegistrationRequestService registrationRequestService,
            AuditRepository auditRepository, AuditService auditService) {
        this(repository, emergencyService, profileService, registrationRequestService, auditRepository, auditService,
                Clock.systemUTC());
    }

    AdministrationServiceImpl(AdministrationRepository repository, EmergencyService emergencyService,
            ProfileService profileService, RegistrationRequestService registrationRequestService,
            AuditRepository auditRepository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.emergencyService = emergencyService;
        this.profileService = profileService;
        this.registrationRequestService = registrationRequestService;
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override @Transactional
    public DashboardResponse dashboard(AuthenticatedIdentity identity) {
        requireAdministrator(identity);
        var values = repository.dashboard();
        auditAlertAccess(identity.userId(), "DASHBOARD", identity.userId(), "Administrative dashboard viewed.");
        return new DashboardResponse(values.activeCount(), values.inProgressCount(), values.offlineCount());
    }

    @Override @Transactional
    public PageResponse<EmergencyResponse> emergencies(AuthenticatedIdentity identity, String status, int page, int size) {
        requireAdministrator(identity);
        String filter = normalizeStatusFilter(status);
        auditAlertAccess(identity.userId(), "EMERGENCY_LIST", identity.userId(), "Administrative emergency list viewed.");
        return new PageResponse<>(repository.findEmergencies(filter, size, page * size).stream().map(this::emergency).toList(),
                page, size, repository.countEmergencies(filter));
    }

    @Override @Transactional
    public void startAttention(AuthenticatedIdentity identity, UUID emergencyId) {
        requireAdministrator(identity);
        UUID subjectUserId = repository.findEmergencyOwner(emergencyId).orElseThrow(ResourceNotFoundException::new);
        if (emergencyService.startAttention(identity, emergencyId)) {
            auditService.record(AuditEvent.success(identity.userId(), subjectUserId, "ALERT_STATUS_CHANGED", "EMERGENCY", emergencyId,
                    Map.of("status", "ACTIVE"), Map.of("status", "IN_PROGRESS"), "Administrative attention started."));
        }
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<AuthenticatedUserResponse> users(AuthenticatedIdentity identity, int page, int size) {
        requireAdministrator(identity);
        return new PageResponse<>(repository.findUsers(size, page * size).stream().map(this::profile).toList(), page, size,
                repository.countUsers());
    }

    @Override @Transactional
    public RegistrationRequestResponse registerUser(AuthenticatedIdentity identity, AdminRegistrationRequestInput input) {
        requireAdministrator(identity);
        return registrationRequestService.startAdministrativeRegistration(input);
    }

    @Override @Transactional
    public AuthenticatedUserResponse user(AuthenticatedIdentity identity, UUID userId) {
        requireAdministrator(identity);
        UserProfileData user = repository.findUser(userId).orElseThrow(ResourceNotFoundException::new);
        auditService.record(AuditEvent.success(identity.userId(), user.id(), "USER_PROFILE_VIEWED", "USER", user.id(),
                null, null, "Administrative user profile viewed."));
        return profile(user);
    }

    @Override @Transactional
    public AuthenticatedUserResponse changeStatus(AuthenticatedIdentity identity, UUID userId, String status) {
        requireAdministrator(identity);
        ManagedUserData user = repository.lockManagedUser(userId).orElseThrow(ResourceNotFoundException::new);
        if (!"USER".equals(user.role())) throw new ForbiddenException();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) throw new RuleViolationException();
        if ("DISABLED".equals(status) && "ENABLED".equals(user.accountStatus()) && !isInactiveForTwoMonths(user)) {
            throw new RuleViolationException();
        }
        if (!status.equals(user.accountStatus())) {
            Instant now = clock.instant();
            repository.updateAccountStatus(user.id(), status, now);
            if ("DISABLED".equals(status)) repository.revokeAllSessions(user.id(), now);
            auditService.record(AuditEvent.success(identity.userId(), user.id(), "ACCOUNT_STATUS_CHANGED", "USER", user.id(),
                    Map.of("accountStatus", user.accountStatus()), Map.of("accountStatus", status),
                    "Administrative account status changed."));
        }
        return repository.findUser(user.id()).map(this::profile).orElseThrow(ResourceNotFoundException::new);
    }

    @Override @Transactional
    public void deleteUser(AuthenticatedIdentity identity, UUID userId) {
        requireAdministrator(identity);
        profileService.deleteDisabledUserForAdministration(userId);
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(AuthenticatedIdentity identity, int page, int size) {
        requireAdministrator(identity);
        return new PageResponse<>(auditRepository.findPage(size, page * size).stream()
                .map(log -> new AuditLogResponse(log.id(), log.action(), log.createdAt())).toList(), page, size,
                auditRepository.count());
    }

    @Override @Transactional
    public boolean disableUserIfStillInactive(UUID userId) {
        ManagedUserData user = repository.lockManagedUser(userId).orElse(null);
        if (user == null || !"USER".equals(user.role()) || !"ENABLED".equals(user.accountStatus())) return false;
        Instant now = clock.instant();
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(3).toInstant();
        if (!isInactiveSince(user, cutoff) || !repository.disableUserIfStillInactive(user.id(), cutoff, now)) return false;
        repository.revokeAllSessions(user.id(), now);
        auditService.record(AuditEvent.success(null, user.id(), "ACCOUNT_STATUS_CHANGED", "USER", user.id(),
                Map.of("accountStatus", "ENABLED"), Map.of("accountStatus", "DISABLED"),
                "Account disabled by the inactivity job."));
        return true;
    }

    @Override @Transactional
    public void replaceAdministrator(UUID targetUserId) {
        ReplacementUserData outgoing = repository.lockCurrentAdministrator().orElseThrow(RuleViolationException::new);
        ReplacementUserData target = repository.lockReplacementTarget(targetUserId).orElseThrow(ResourceNotFoundException::new);
        if (outgoing.id().equals(target.id()) || !"USER".equals(target.role()) || !"ENABLED".equals(target.accountStatus())) {
            throw new RuleViolationException();
        }
        repository.replaceAdministrator(outgoing.id(), target.id(), clock.instant());
    }

    private void requireAdministrator(AuthenticatedIdentity identity) {
        if (identity == null || !"ENTITY_ADMIN".equals(identity.role()) || !repository.isEnabledAdministrator(identity.userId())) {
            throw new ForbiddenException();
        }
    }

    private boolean isInactiveForTwoMonths(ManagedUserData user) {
        Instant cutoff = clock.instant().atZone(ZoneOffset.UTC).minusMonths(2).toInstant();
        return isInactiveSince(user, cutoff);
    }

    private boolean isInactiveSince(ManagedUserData user, Instant cutoff) {
        Instant lastActivity = user.lastActivityAt() != null ? user.lastActivityAt()
                : user.lastLoginAt() != null ? user.lastLoginAt() : user.createdAt();
        return !lastActivity.isAfter(cutoff);
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        if (!"ACTIVE".equals(status) && !"IN_PROGRESS".equals(status) && !"OFFLINE".equals(status)
                && !"FINALIZED".equals(status)) throw new RuleViolationException();
        return status;
    }

    private void auditAlertAccess(UUID actorUserId, String entityType, UUID entityId, String description) {
        auditService.record(AuditEvent.success(actorUserId, null, "ALERT_VIEWED", entityType, entityId,
                null, null, description));
    }

    private EmergencyResponse emergency(com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData value) {
        return new EmergencyResponse(value.id(), value.status(), value.previousOperationalStatus(), value.startedAt(),
                value.lastHeartbeatAt(), value.finalizedAt());
    }

    private AuthenticatedUserResponse profile(UserProfileData user) {
        return new AuthenticatedUserResponse(user.id(), user.username(), user.firstNames(), user.lastNames(), user.email(),
                user.phone(), user.role(), user.accountStatus());
    }
}
