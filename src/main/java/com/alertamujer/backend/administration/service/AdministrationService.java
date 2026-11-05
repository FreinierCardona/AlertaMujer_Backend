package com.alertamujer.backend.administration.service;

import com.alertamujer.backend.administration.dto.response.AuditLogResponse;
import com.alertamujer.backend.administration.dto.response.DashboardResponse;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.util.UUID;

/** Casos de uso administrativos; las reglas de negocio siguen a cargo de los servicios de identidad y emergencia. */
public interface AdministrationService {
    DashboardResponse dashboard(AuthenticatedIdentity identity);

    PageResponse<EmergencyResponse> emergencies(AuthenticatedIdentity identity, String status, int page, int size);

    void startAttention(AuthenticatedIdentity identity, UUID emergencyId);

    PageResponse<AuthenticatedUserResponse> users(AuthenticatedIdentity identity, int page, int size);

    RegistrationRequestResponse registerUser(AuthenticatedIdentity identity, AdminRegistrationRequestInput input);

    AuthenticatedUserResponse user(AuthenticatedIdentity identity, UUID userId);

    AuthenticatedUserResponse changeStatus(AuthenticatedIdentity identity, UUID userId, String status);

    void deleteUser(AuthenticatedIdentity identity, UUID userId);

    PageResponse<AuditLogResponse> auditLogs(AuthenticatedIdentity identity, int page, int size);

    /** Operación interna del planificador que no se expone mediante HTTP. */
    boolean disableUserIfStillInactive(UUID userId);

    void replaceAdministrator(UUID targetUserId);
}
