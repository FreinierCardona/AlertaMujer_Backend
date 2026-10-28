package com.alertamujer.backend.shared.errors;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RegistrationConflictExceptionTest {
    @Test void mapsOnlyKnownUniqueConstraintsWithoutExposingDatabaseDetails() {
        for (String field : java.util.List.of("username", "phone", "email")) {
            var error = RegistrationConflictException.fromConstraint(new DataIntegrityViolationException(
                    "private value", new RuntimeException("ux_registration_requests_pending_" + field)));
            assertThat(error).isInstanceOf(RegistrationConflictException.class);
            assertThat(((RegistrationConflictException) error).field()).isEqualTo(field);
            assertThat(((RegistrationConflictException) error).fieldMessage()).doesNotContain("private value");
        }
        assertThat(RegistrationConflictException.fromConstraint(new DataIntegrityViolationException("unrelated")))
                .isExactlyInstanceOf(RuleViolationException.class);
    }
}
