package com.alertamujer.backend.contacts.controller;

import com.alertamujer.backend.contacts.dto.request.ContactInvitationInput;
import com.alertamujer.backend.contacts.dto.response.ContactInvitationResponse;
import com.alertamujer.backend.contacts.dto.response.ContactResponse;
import com.alertamujer.backend.contacts.dto.response.DirectoryUserResponse;
import com.alertamujer.backend.contacts.dto.response.PageResponse;
import com.alertamujer.backend.contacts.service.ContactService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageParameters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the contact-directory contract. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/directory")
    public PageResponse<DirectoryUserResponse> directory(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) @Size(min = 1, max = 50) String query,
            @Valid PageParameters parameters) {
        return contactService.directory(identity, query, parameters.getPage(), parameters.getSize());
    }

    @PostMapping("/contact-invitations")
    public ResponseEntity<ContactInvitationResponse> invite(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody ContactInvitationInput input) {
        ContactService.InvitationResult result = contactService.invite(identity, input);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.invitation());
    }

    @PostMapping("/contact-invitations/{contactId}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID contactId) {
        contactService.accept(identity, contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contact-invitations/{contactId}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID contactId) {
        contactService.reject(identity, contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contact-invitations/{contactId}/reinvite")
    public ContactInvitationResponse reinvite(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID contactId) {
        return contactService.reinvite(identity, contactId);
    }

    @GetMapping("/contacts")
    public PageResponse<ContactResponse> ownContacts(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid PageParameters parameters) {
        return contactService.ownContacts(identity, parameters.getPage(), parameters.getSize());
    }
}
