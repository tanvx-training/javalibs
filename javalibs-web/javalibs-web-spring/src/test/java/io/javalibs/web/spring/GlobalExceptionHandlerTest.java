package io.javalibs.web.spring;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalibs.web.exception.ApiException;
import io.javalibs.web.exception.ResourceNotFoundException;
import io.javalibs.web.CommonErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc standalone tests for {@link GlobalExceptionHandler}, driving each handled
 * exception type through a dummy controller.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void apiExceptionUsesErrorCodeStatusAndCode() throws Exception {
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ERR_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/forbidden"));
    }

    @Test
    void resourceNotFoundExceptionMapsTo404() throws Exception {
        mockMvc.perform(get("/orders/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order with id 42 not found"));
    }

    @Test
    void beanValidationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("name", "age")));
    }

    @Test
    void constraintViolationReturnsMappedViolations() throws Exception {
        mockMvc.perform(get("/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))));
    }

    @Test
    void malformedBodyReturns400WithGenericMessage() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void typeMismatchReturns400() throws Exception {
        mockMvc.perform(get("/orders/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Parameter 'id' has invalid value 'abc'"));
    }

    @Test
    void noResourceFoundReturns404() throws Exception {
        mockMvc.perform(get("/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_RESOURCE_NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(post("/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("ERR_METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERR_INTERNAL"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
    }

    @Test
    void traceIdIsResolvedFromMdc() throws Exception {
        MDC.put("traceId", "trace-abc-123");
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.traceId").value("trace-abc-123"));
    }

    @Test
    void traceIdFallsBackToCorrelationId() throws Exception {
        MDC.put("correlationId", "corr-9");
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.traceId").value("corr-9"));
    }

    /**
     * Dummy controller throwing each exception type handled by the advice.
     */
    @RestController
    static class DummyController {

        record CreateUserRequest(@NotBlank String name, @Min(18) int age) {
        }

        @GetMapping("/forbidden")
        String forbidden() {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Access denied");
        }

        @GetMapping("/orders/{id}")
        String order(@PathVariable long id) {
            throw new ResourceNotFoundException("Order", id);
        }

        @PostMapping("/users")
        String createUser(@jakarta.validation.Valid @RequestBody CreateUserRequest request) {
            return "created";
        }

        @GetMapping("/constraint")
        String constraint() {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<CreateUserRequest>> violations =
                        factory.getValidator().validate(new CreateUserRequest("", 10));
                throw new ConstraintViolationException(violations);
            }
        }

        @GetMapping("/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/static/missing.png");
        }

        @GetMapping("/only-get")
        String onlyGet() {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
