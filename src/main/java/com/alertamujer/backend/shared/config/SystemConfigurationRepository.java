package com.alertamujer.backend.shared.config;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Expone únicamente la lectura de la fila única necesaria al iniciar la aplicación. */
interface SystemConfigurationRepository extends Repository<SystemConfigurationEntity, Short> {

    Optional<SystemConfigurationEntity> findByConfigurationId(Short configurationId);
}
