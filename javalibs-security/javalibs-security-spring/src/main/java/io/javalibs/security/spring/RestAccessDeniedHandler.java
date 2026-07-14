package io.javalibs.security.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AccessDeniedHandler} for stateless REST APIs: writes a {@code 403 Forbidden} JSON body
 * of the form
 * <pre>{@code {"timestamp":"...","status":403,"code":"ERR_FORBIDDEN","message":"..."}}</pre>
 * instead of rendering an error page.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    /** Machine readable error code emitted for forbidden requests. */
    public static final String ERROR_CODE = "ERR_FORBIDDEN";

    private final ObjectMapper objectMapper;

    /**
     * Creates a handler with a private default {@link ObjectMapper}.
     */
    public RestAccessDeniedHandler() {
        this(new ObjectMapper());
    }

    /**
     * Creates a handler using the given {@link ObjectMapper}.
     *
     * @param objectMapper the mapper used to serialize the error body, never {@code null}
     */
    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("code", ERROR_CODE);
        body.put("message", (accessDeniedException != null && accessDeniedException.getMessage() != null)
                ? accessDeniedException.getMessage()
                : "Access denied");

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
