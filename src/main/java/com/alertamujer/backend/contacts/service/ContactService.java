package com.alertamujer.backend.contacts.service;

import com.alertamujer.backend.contacts.dto.request.ContactInvitationInput;
import com.alertamujer.backend.contacts.dto.response.ContactInvitationResponse;
import com.alertamujer.backend.contacts.dto.response.ContactResponse;
import com.alertamujer.backend.contacts.dto.response.DirectoryUserResponse;
import com.alertamujer.backend.contacts.dto.response.PageResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.util.UUID;

/** Contact directory and consented-relationship use cases from HU-API-011. */
public interface ContactService {
    PageResponse<DirectoryUserResponse> directory(AuthenticatedIdentity identity, String query, int page, int size);
    InvitationResult invite(AuthenticatedIdentity identity, ContactInvitationInput input);
    void accept(AuthenticatedIdentity identity, UUID contactId);
    void reject(AuthenticatedIdentity identity, UUID contactId);
    ContactInvitationResponse reinvite(AuthenticatedIdentity identity, UUID contactId);
    PageResponse<ContactResponse> ownContacts(AuthenticatedIdentity identity, int page, int size);

    record InvitationResult(ContactInvitationResponse invitation, boolean created) { }
}
