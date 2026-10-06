package com.alertamujer.backend.identity.repository;

import com.alertamujer.backend.identity.model.RegistrationRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Queries only the identity tables needed before a user account exists. */
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequestEntity, UUID> {

    @Query(value = """
            select exists (select 1 from identity.users
                           where username = :username or email = :email or phone = :phone)
                or exists (select 1 from identity.registration_requests
                           where status = 'PENDING'
                             and (username = :username or email = :email or phone = :phone))
            """, nativeQuery = true)
    boolean existsIdentityConflict(@Param("username") String username,
            @Param("email") String email, @Param("phone") String phone);
}
