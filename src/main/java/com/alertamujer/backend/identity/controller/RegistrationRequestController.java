package com.alertamujer.backend.identity.controller;

import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the two ways of starting the identity registration flow. */
@RestController
@RequestMapping("/api/v1")
public class RegistrationRequestController {

    private final RegistrationRequestService registrationRequestService;

    public RegistrationRequestController(RegistrationRequestService registrationRequestService) {
        this.registrationRequestService = registrationRequestService;
    }

    @PostMapping("/registration-requests")
    public ResponseEntity<RegistrationRequestResponse> createPublicRequest(
            @Valid @RequestBody RegistrationRequestInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationRequestService.startPublicRegistration(input));
    }

    @PostMapping("/admin/users")
    public ResponseEntity<RegistrationRequestResponse> createAdministrativeRequest(
            @Valid @RequestBody AdminRegistrationRequestInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationRequestService.startAdministrativeRegistration(input));
    }
}
