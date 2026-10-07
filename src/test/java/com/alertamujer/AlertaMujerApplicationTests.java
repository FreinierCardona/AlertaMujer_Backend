package com.alertamujer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.shared.config.ApplicationDatasourceGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

class AlertaMujerApplicationTests {

    @Test
    void rejectsDatasourceAccountsOtherThanTheApplicationRole() {
        Environment environment = mock(Environment.class);
        when(environment.getRequiredProperty("spring.datasource.username"))
                .thenReturn("alertamujer_migrator");

        ApplicationDatasourceGuard guard = new ApplicationDatasourceGuard(environment);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The datasource must use the alertamujer_app role");
    }

    @Test
    void acceptsTheLeastPrivilegeApplicationRole() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getRequiredProperty("spring.datasource.username"))
                .thenReturn("alertamujer_app");

        new ApplicationDatasourceGuard(environment).run(new DefaultApplicationArguments(new String[0]));
    }
}
