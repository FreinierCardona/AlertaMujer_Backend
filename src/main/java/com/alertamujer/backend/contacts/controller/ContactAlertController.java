package com.alertamujer.backend.contacts.controller;

import com.alertamujer.backend.contacts.dto.response.ContactAlertDetailResponse;
import com.alertamujer.backend.contacts.dto.response.ContactAlertResponse;
import com.alertamujer.backend.contacts.service.ContactAlertService;
import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageParameters;
import com.alertamujer.backend.shared.validation.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador HTTP del panel de emergencias para contactos autenticados, disponible solo para lectura. */
@RestController
@Validated
@RequestMapping("/api/v1/me/contact-alerts")
public class ContactAlertController {
    private final ContactAlertService service;

    public ContactAlertController(ContactAlertService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ContactAlertResponse> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid PageParameters parameters) {
        return service.list(identity, parameters.getPage(), parameters.getSize());
    }

    @GetMapping("/{emergencyId}")
    public ContactAlertDetailResponse detail(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID emergencyId) {
        return service.detail(identity, emergencyId);
    }

    @GetMapping("/{emergencyId}/evidences")
    public List<EvidenceResponse> evidences(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID emergencyId) {
        return service.evidences(identity, emergencyId);
    }

    @GetMapping("/{emergencyId}/evidences/{evidenceId}/content")
    public ResponseEntity<InputStreamResource> evidenceContent(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID emergencyId, @PathVariable UUID evidenceId) {
        ContactAlertService.EvidenceContent content = service.evidenceContent(identity, emergencyId, evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/webp")).contentLength(content.length())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(new InputStreamResource(content.stream()));
    }
}
