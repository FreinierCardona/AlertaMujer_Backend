package com.alertamujer.backend.shared.config;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Deliberately exposes only the singleton read required at application startup. */
interface SystemConfigurationRepository extends Repository<SystemConfigurationEntity, Short> {

    Optional<SystemConfigurationEntity> findByConfigurationId(Short configurationId);
}
