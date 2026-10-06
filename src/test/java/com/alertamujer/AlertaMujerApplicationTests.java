package com.alertamujer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertamujer.backend.shared.config.ApplicationDatasourceGuard;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

class AlertaMujerApplicationTests {

    @Test
    void rejectsDatasourceAccountsOtherThanTheApplicationRole() {
        Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.getRequiredProperty("spring.datasource.username"))
                .thenReturn("alertamujer_migrator");

        ApplicationDatasourceGuard guard = new ApplicationDatasourceGuard(environment);

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The datasource must use the alertamujer_app role");
    }

    @Test
    void acceptsTheLeastPrivilegeApplicationRole() throws Exception {
        Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.getRequiredProperty("spring.datasource.username"))
                .thenReturn("alertamujer_app");

        new ApplicationDatasourceGuard(environment).run(new DefaultApplicationArguments(new String[0]));
    }
}
