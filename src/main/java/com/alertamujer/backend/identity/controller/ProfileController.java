package com.alertamujer.backend.identity.controller;

import com.alertamujer.backend.identity.dto.request.ContactChangeRequestInput;
import com.alertamujer.backend.identity.dto.request.ContactChangeVerifyInput;
import com.alertamujer.backend.identity.dto.request.ProfileUpdateInput;
import com.alertamujer.backend.identity.dto.request.SosMessageInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.SosMessageResponse;
import com.alertamujer.backend.identity.service.ProfileService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the authenticated own-profile routes in HU-API-010. */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService profileService;
    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    @GetMapping
    public AuthenticatedUserResponse getProfile(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return profileService.getProfile(identity);
    }
    @PatchMapping
    public AuthenticatedUserResponse updateProfile(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody ProfileUpdateInput input) { return profileService.updateProfile(identity, input); }
    @PostMapping("/terms-acceptance")
    public ResponseEntity<Void> acceptTerms(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        profileService.acceptTerms(identity); return ResponseEntity.noContent().build();
    }
    @PostMapping("/contact-changes")
    public ResponseEntity<OtpIssuedResponse> requestContactChange(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody ContactChangeRequestInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.requestContactChange(identity, input));
    }
    @PostMapping("/contact-changes/verify")
    public ResponseEntity<Void> verifyContactChange(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody ContactChangeVerifyInput input) {
        profileService.verifyContactChange(identity, input); return ResponseEntity.noContent().build();
    }
    @GetMapping("/emergency-settings")
    public SosMessageResponse getEmergencySettings(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return profileService.getEmergencySettings(identity);
    }
    @PutMapping("/emergency-settings")
    public SosMessageResponse saveEmergencySettings(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody SosMessageInput input) { return profileService.saveEmergencySettings(identity, input); }
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        profileService.deleteAccount(identity); return ResponseEntity.noContent().build();
    }
}
