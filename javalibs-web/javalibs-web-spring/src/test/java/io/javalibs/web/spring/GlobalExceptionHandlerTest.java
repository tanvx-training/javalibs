package io.javalibs.web.spring;

import java.io.IOException;
import java.util.Set;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc standalone tests for {@link GlobalExceptionHandler}, driving each handled
 * exception type through a dummy controller.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private ch.qos.logback.classic.Logger handlerLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        handlerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        originalLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        handlerLogger.detachAppender(logs);
        logs.stop();
        handlerLogger.setLevel(originalLevel);
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

    /**
     * A client disconnecting mid-async-response (SSE stream, long download) is normal
     * traffic, not a server fault: Spring's own {@code DefaultHandlerExceptionResolver}
     * discards {@code AsyncRequestNotUsableException} silently. The catch-all handler must
     * not claim it first - doing so logs a stack trace at ERROR per disconnect and then
     * fails again trying to write the JSON body into a response whose Content-Type is
     * already pinned to {@code text/event-stream}.
     */
    @Test
    void clientDisconnectOnAsyncResponseIsDiscardedQuietly() throws Exception {
        mockMvc.perform(get("/stream-gone"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(logs.list)
                .as("client disconnect must not be logged at ERROR/WARN")
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
        assertThat(logs.list)
                .as("but it must still be traceable at DEBUG")
                .anyMatch(event -> event.getLevel() == Level.DEBUG
                        && event.getFormattedMessage().contains("/stream-gone"));
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

        /** Mirrors an SSE stream whose client went away: Content-Type already committed. */
        @GetMapping("/stream-gone")
        String streamGone(HttpServletResponse response) throws AsyncRequestNotUsableException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            throw new AsyncRequestNotUsableException(
                    "Servlet container error notification for disconnected client",
                    new IOException("Broken pipe"));
        }
    }
}
