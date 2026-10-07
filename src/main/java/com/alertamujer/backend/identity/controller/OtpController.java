package com.alertamujer.backend.identity.controller;

import com.alertamujer.backend.identity.dto.request.ChannelRequest;
import com.alertamujer.backend.identity.dto.request.OtpVerificationInput;
import com.alertamujer.backend.identity.dto.request.PasswordResetOtpRequest;
import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.service.OtpService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for public registration and password-recovery OTP issuance. */
@RestController
@RequestMapping("/api/v1")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/registration-requests/{registrationRequestId}/verification-codes")
    public ResponseEntity<OtpIssuedResponse> issueRegistrationCode(
            @PathVariable UUID registrationRequestId, @Valid @RequestBody ChannelRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(otpService.issueRegistrationCode(registrationRequestId, input.channel()));
    }

    @PostMapping("/registration-requests/{registrationRequestId}/verification-codes/verify")
    public RegistrationVerificationResponse verifyRegistrationCode(
            @PathVariable UUID registrationRequestId, @Valid @RequestBody OtpVerificationInput input) {
        return otpService.verifyRegistrationCode(registrationRequestId, input.channel(), input.code());
    }

    @PostMapping("/auth/password-reset/verification-codes")
    public ResponseEntity<Void> requestPasswordResetCode(@Valid @RequestBody PasswordResetOtpRequest input) {
        otpService.requestPasswordResetCode(input.email());
        return ResponseEntity.accepted().build();
    }
}
