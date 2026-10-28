package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.request.PhoneFirstRegistrationInput;
import com.alertamujer.backend.identity.dto.request.RegistrationCredentialsInput;
import com.alertamujer.backend.identity.dto.response.PhoneFirstRegistrationResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationStageResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import java.util.UUID;

/** Flujo público v2; cada operación de continuación exige la prueba opaca. */
public interface PhoneFirstRegistrationService {

    PhoneFirstRegistrationResponse start(PhoneFirstRegistrationInput input);

    RegistrationStageResponse issueCode(UUID registrationRequestId, String registrationProof, OtpChannel channel);

    RegistrationStageResponse completeCredentials(UUID registrationRequestId, String registrationProof,
            RegistrationCredentialsInput input);

    RegistrationVerificationResponse verifyCode(UUID registrationRequestId, String registrationProof,
            OtpChannel channel, String code);
}
