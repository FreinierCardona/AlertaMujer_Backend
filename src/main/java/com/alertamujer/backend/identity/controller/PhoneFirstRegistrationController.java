package com.alertamujer.backend.identity.controller;

import com.alertamujer.backend.identity.dto.request.ChannelRequest;
import com.alertamujer.backend.identity.dto.request.PhoneFirstRegistrationInput;
import com.alertamujer.backend.identity.dto.request.RegistrationCredentialsInput;
import com.alertamujer.backend.identity.dto.request.V2OtpVerificationInput;
import com.alertamujer.backend.identity.dto.response.PhoneFirstRegistrationResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationStageResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.service.PhoneFirstRegistrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador HTTP del contrato público v2 de registro que comienza por el teléfono. */
@RestController
@RequestMapping("/api/v2/registration-requests")
public class PhoneFirstRegistrationController {

    private static final String REGISTRATION_PROOF_HEADER = "X-Registration-Proof";

    private final PhoneFirstRegistrationService service;

    public PhoneFirstRegistrationController(PhoneFirstRegistrationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PhoneFirstRegistrationResponse> start(@Valid @RequestBody PhoneFirstRegistrationInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(input));
    }

    @PostMapping("/{registrationRequestId}/verification-codes")
    public ResponseEntity<RegistrationStageResponse> issueCode(
            @PathVariable UUID registrationRequestId,
            @RequestHeader(REGISTRATION_PROOF_HEADER) String registrationProof,
            @Valid @RequestBody ChannelRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.issueCode(registrationRequestId, registrationProof, input.channel()));
    }

    @PostMapping("/{registrationRequestId}/verification-codes/verify")
    public RegistrationVerificationResponse verifyCode(
            @PathVariable UUID registrationRequestId,
            @RequestHeader(REGISTRATION_PROOF_HEADER) String registrationProof,
            @Valid @RequestBody V2OtpVerificationInput input) {
        return service.verifyCode(registrationRequestId, registrationProof, input.channel(), input.code());
    }

    @PutMapping("/{registrationRequestId}/credentials")
    public RegistrationStageResponse completeCredentials(
            @PathVariable UUID registrationRequestId,
            @RequestHeader(REGISTRATION_PROOF_HEADER) String registrationProof,
            @Valid @RequestBody RegistrationCredentialsInput input) {
        return service.completeCredentials(registrationRequestId, registrationProof, input);
    }
}
