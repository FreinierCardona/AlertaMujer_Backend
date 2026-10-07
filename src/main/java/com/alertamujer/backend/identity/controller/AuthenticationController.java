package com.alertamujer.backend.identity.controller;

import com.alertamujer.backend.identity.dto.request.LoginInput;
import com.alertamujer.backend.identity.dto.request.PasswordChangeInput;
import com.alertamujer.backend.identity.dto.request.PasswordResetConfirmInput;
import com.alertamujer.backend.identity.dto.request.RefreshInput;
import com.alertamujer.backend.identity.dto.response.SessionResponse;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the contract's login, renewal, logout, and password routes. */
@RestController
@RequestMapping("/api/v1")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/auth/login")
    public SessionResponse login(@Valid @RequestBody LoginInput input) {
        return authenticationService.login(input);
    }

    @PostMapping("/auth/refresh")
    public SessionResponse refresh(@Valid @RequestBody RefreshInput input) {
        return authenticationService.refresh(input.refreshToken());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        authenticationService.logout(identity);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmInput input) {
        authenticationService.confirmPasswordReset(input);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody PasswordChangeInput input) {
        authenticationService.changePassword(identity, input);
        return ResponseEntity.noContent().build();
    }
}
