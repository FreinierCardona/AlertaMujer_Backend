package com.alertamujer.backend.emergency.controller;

import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageParameters;
import com.alertamujer.backend.shared.validation.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the owner's finalized SOS history without adding administrative queries. */
@RestController
@RequestMapping("/api/v1/me/emergencies")
public class OwnEmergencyHistoryController {

    private final EmergencyService emergencyService;

    public OwnEmergencyHistoryController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @GetMapping
    public PageResponse<EmergencyResponse> ownHistory(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid PageParameters parameters) {
        return emergencyService.ownHistory(identity, parameters.getPage(), parameters.getSize());
    }
}
