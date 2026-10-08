package com.alertamujer.backend.administration.controller;

import com.alertamujer.backend.administration.dto.request.AccountStatusInput;
import com.alertamujer.backend.administration.dto.response.AuditLogResponse;
import com.alertamujer.backend.administration.dto.response.DashboardResponse;
import com.alertamujer.backend.administration.service.AdministrationService;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageParameters;
import com.alertamujer.backend.shared.validation.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the administrative panel routes defined in OpenAPI. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdministrationController {
    private final AdministrationService service;
    public AdministrationController(AdministrationService service) { this.service = service; }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedIdentity identity) { return service.dashboard(identity); }

    @GetMapping("/emergencies")
    public PageResponse<EmergencyResponse> emergencies(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) String status, @Valid PageParameters parameters) {
        return service.emergencies(identity, status, parameters.getPage(), parameters.getSize());
    }

    @PostMapping("/emergencies/{emergencyId}/attention")
    public ResponseEntity<Void> startAttention(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID emergencyId) {
        service.startAttention(identity, emergencyId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public PageResponse<AuthenticatedUserResponse> users(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid PageParameters parameters) { return service.users(identity, parameters.getPage(), parameters.getSize()); }

    @GetMapping("/users/{userId}")
    public AuthenticatedUserResponse user(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID userId) {
        return service.user(identity, userId);
    }

    @PatchMapping("/users/{userId}/status")
    public AuthenticatedUserResponse changeStatus(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID userId, @Valid @RequestBody AccountStatusInput input) {
        return service.changeStatus(identity, userId, input.status());
    }

    @DeleteMapping("/users/{userId}/deletion")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID userId) {
        service.deleteUser(identity, userId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid PageParameters parameters) { return service.auditLogs(identity, parameters.getPage(), parameters.getSize()); }
}
