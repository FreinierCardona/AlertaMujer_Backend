package com.alertamujer.backend.emergency.controller;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for an owner's SOS, without notification or later lifecycle transitions. */
@RestController
@RequestMapping("/api/v1/emergencies")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @PostMapping
    public ResponseEntity<EmergencyResponse> createOrRecover(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody EmergencyCreateInput input) {
        EmergencyService.CreationResult result = emergencyService.createOrRecover(identity, input);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.emergency());
    }

    @GetMapping("/active")
    public EmergencyResponse active(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return emergencyService.active(identity);
    }

    @GetMapping("/{emergencyId}")
    public EmergencyResponse ownEmergency(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID emergencyId) {
        return emergencyService.ownEmergency(identity, emergencyId);
    }
}
