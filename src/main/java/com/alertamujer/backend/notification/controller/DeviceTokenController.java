package com.alertamujer.backend.notification.controller;

import com.alertamujer.backend.notification.dto.request.DeviceTokenInput;
import com.alertamujer.backend.notification.service.NotificationService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Own-device endpoint; it never returns the submitted FCM token. */
@RestController
@RequestMapping("/api/v1/me/device-tokens")
public class DeviceTokenController {
    private final NotificationService notificationService;

    public DeviceTokenController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<Void> register(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody DeviceTokenInput input) {
        boolean created = notificationService.registerDeviceToken(identity, input);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }
}
