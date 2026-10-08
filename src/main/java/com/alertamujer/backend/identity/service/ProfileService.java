package com.alertamujer.backend.identity.service;

import com.alertamujer.backend.identity.dto.request.ContactChangeRequestInput;
import com.alertamujer.backend.identity.dto.request.ContactChangeVerifyInput;
import com.alertamujer.backend.identity.dto.request.ProfileUpdateInput;
import com.alertamujer.backend.identity.dto.request.SosMessageInput;
import com.alertamujer.backend.identity.dto.response.AuthenticatedUserResponse;
import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.SosMessageResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;

/** Own-profile use cases defined by HU-API-010. */
public interface ProfileService {
    AuthenticatedUserResponse getProfile(AuthenticatedIdentity identity);
    AuthenticatedUserResponse updateProfile(AuthenticatedIdentity identity, ProfileUpdateInput input);
    OtpIssuedResponse requestContactChange(AuthenticatedIdentity identity, ContactChangeRequestInput input);
    void verifyContactChange(AuthenticatedIdentity identity, ContactChangeVerifyInput input);
    void acceptTerms(AuthenticatedIdentity identity);
    SosMessageResponse getEmergencySettings(AuthenticatedIdentity identity);
    SosMessageResponse saveEmergencySettings(AuthenticatedIdentity identity, SosMessageInput input);
    void deleteAccount(AuthenticatedIdentity identity);
    void deleteDisabledUserForAdministration(java.util.UUID userId);
}
