package com.alertamujer.backend.notification.service;

import com.alertamujer.backend.notification.dto.request.DeviceTokenInput;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;

/** Authenticated notification settings owned by the current user. */
public interface NotificationService {
    /** @return true only when a previously unseen token was registered. */
    boolean registerDeviceToken(AuthenticatedIdentity identity, DeviceTokenInput input);
}
