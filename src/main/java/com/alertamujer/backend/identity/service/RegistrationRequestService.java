package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;

/** Starts the temporary registration flow; it never creates a user account. */
public interface RegistrationRequestService {

    RegistrationRequestResponse startPublicRegistration(RegistrationRequestInput input);

    RegistrationRequestResponse startAdministrativeRegistration(AdminRegistrationRequestInput input);
}
