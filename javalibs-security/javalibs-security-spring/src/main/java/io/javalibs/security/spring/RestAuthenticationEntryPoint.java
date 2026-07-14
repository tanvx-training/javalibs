package io.javalibs.security.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.security.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AuthenticationEntryPoint} for stateless REST APIs: instead of redirecting to a login
 * page it writes a {@code 401 Unauthorized} JSON body of the form
 * <pre>{@code {"timestamp":"...","status":401,"code":"ERR_UNAUTHORIZED","message":"..."}}</pre>
 *
 * <p>If the {@link JwtAuthenticationFilter} stashed an {@link InvalidTokenException} on the
 * request, its message is used so clients learn <em>why</em> the token was rejected.</p>
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Machine readable error code emitted for unauthorized requests. */
    public static final String ERROR_CODE = "ERR_UNAUTHORIZED";

    private final ObjectMapper objectMapper;

    /**
     * Creates an entry point with a private default {@link ObjectMapper}.
     */
    public RestAuthenticationEntryPoint() {
        this(new ObjectMapper());
    }

    /**
     * Creates an entry point using the given {@link ObjectMapper}.
     *
     * @param objectMapper the mapper used to serialize the error body, never {@code null}
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = resolveMessage(request, authException);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("code", ERROR_CODE);
        body.put("message", message);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveMessage(HttpServletRequest request, AuthenticationException authException) {
        if (request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
                instanceof InvalidTokenException tokenException) {
            return tokenException.getMessage();
        }
        if (authException != null && authException.getMessage() != null) {
            return authException.getMessage();
        }
        return "Authentication required";
    }
}
