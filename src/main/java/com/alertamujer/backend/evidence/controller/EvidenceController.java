package com.alertamujer.backend.evidence.controller;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.evidence.service.EvidenceService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Protected REST routes for evidence metadata and final WebP bytes. */
@RestController
@RequestMapping("/api/v1")
public class EvidenceController {
    private final EvidenceService service;

    public EvidenceController(EvidenceService service) { this.service = service; }

    @PostMapping(path = "/emergencies/{emergencyId}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceResponse> upload(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID emergencyId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(identity, emergencyId, file));
    }

    @GetMapping("/emergencies/{emergencyId}/evidences")
    public List<EvidenceResponse> list(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID emergencyId) {
        return service.list(identity, emergencyId);
    }

    @GetMapping("/evidences/{evidenceId}/content")
    public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID evidenceId) {
        EvidenceService.EvidenceContent content = service.content(identity, evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/webp"))
                .contentLength(content.length()).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(content.stream()));
    }
}
