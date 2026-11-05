package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.request.AdminRegistrationRequestInput;
import com.alertamujer.backend.identity.dto.request.RegistrationRequestInput;
import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;

/** Inicia el flujo temporal de registro; nunca crea una cuenta de usuario. */
public interface RegistrationRequestService {

    RegistrationRequestResponse startPublicRegistration(RegistrationRequestInput input);

    RegistrationRequestResponse startAdministrativeRegistration(AdminRegistrationRequestInput input);
}
