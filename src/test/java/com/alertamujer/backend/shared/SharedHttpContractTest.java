package com.alertamujer.backend.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.validation.PageParameters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class SharedHttpContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsTheValidIncomingRequestIdInHeaderAndBody() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/contract-test/ok").header(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId.toString()))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void replacesAnInvalidRequestIdWithoutReturningTheInvalidValue() throws Exception {
        MvcResult result = mockMvc.perform(get("/contract-test/ok").header(RequestIdFilter.HEADER_NAME, "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andReturn();

        String responseId = result.getResponse().getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(responseId).isNotEqualTo("not-a-uuid");
        assertThat(UUID.fromString(responseId)).isEqualTo(UUID.fromString(responseId));
    }

    @Test
    void returnsSafeErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/contract-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicValue\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void returnsValidationFieldsWithoutEchoingSensitiveValues() throws Exception {
        mockMvc.perform(post("/contract-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicValue\":\"\",\"password\":\"secret-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("publicValue"))
                .andExpect(jsonPath("$.fields[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-value"));
    }

    @Test
    void validatesPaginationBeforeItCanReachARepository() throws Exception {
        mockMvc.perform(get("/contract-test/page").param("page", "0").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsBusinessAndUnexpectedFailuresToSafeErrors() throws Exception {
        mockMvc.perform(get("/contract-test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/contract-test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/contract-test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/contract-test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(get("/contract-test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RULE_VIOLATION"));

        mockMvc.perform(get("/contract-test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("internal-sensitive-detail"));
    }

    @RestController
    @RequestMapping("/contract-test")
    @Validated
    static class ContractTestController {

        @GetMapping("/ok")
        public Object ok(jakarta.servlet.http.HttpServletRequest request) {
            return new RequestIdResponse(request.getAttribute(
                    com.alertamujer.backend.shared.observability.RequestIdContext.ATTRIBUTE_NAME).toString());
        }

        @PostMapping("/validation")
        public void validate(@Valid @RequestBody ValidationInput input) {
            // Validation happens before an application service is invoked.
        }

        @GetMapping("/page")
        public void page(@Valid PageParameters parameters) {
            // A real controller passes only validated parameters to its service.
        }

        @GetMapping("/business")
        public void business() {
            throw new RuleViolationException();
        }

        @GetMapping("/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException();
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenException();
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new ResourceNotFoundException();
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new StateConflictException();
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("internal-sensitive-detail");
        }
    }

    record RequestIdResponse(String requestId) {
    }

    record ValidationInput(
            @NotBlank(message = "must not be blank") String publicValue,
            @NotBlank String password) {
    }
}
