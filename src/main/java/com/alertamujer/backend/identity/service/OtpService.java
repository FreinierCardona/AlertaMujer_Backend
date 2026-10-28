package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import java.util.UUID;

/** Coordina el ciclo de vida de los OTP; HTTP y SMTP siguen siendo adaptadores. */
public interface OtpService {

    OtpIssuedResponse issueRegistrationCode(UUID registrationRequestId, OtpChannel channel);

    RegistrationVerificationResponse verifyRegistrationCode(UUID registrationRequestId, OtpChannel channel, String code);

    void requestPasswordResetCode(String email);

    int purgeTemporaryIdentityData();
}
