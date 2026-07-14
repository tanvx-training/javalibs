package io.javalibs.security.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.security.ExpiredTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RestAuthenticationEntryPoint} and {@link RestAccessDeniedHandler} JSON output.
 */
class RestErrorHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secret");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void entryPointWrites401JsonBody() throws Exception {
        new RestAuthenticationEntryPoint(objectMapper).commence(request, response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("ERR_UNAUTHORIZED");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("message").asText()).isEqualTo("Full authentication is required");
    }

    @Test
    void entryPointPrefersStashedTokenExceptionMessage() throws Exception {
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE,
                new ExpiredTokenException("Token has expired"));

        new RestAuthenticationEntryPoint(objectMapper).commence(request, response,
                new InsufficientAuthenticationException("generic"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("message").asText()).isEqualTo("Token has expired");
    }

    @Test
    void accessDeniedHandlerWrites403JsonBody() throws Exception {
        new RestAccessDeniedHandler(objectMapper).handle(request, response,
                new AccessDeniedException("Access denied: missing role ADMIN"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("code").asText()).isEqualTo("ERR_FORBIDDEN");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("message").asText()).contains("ADMIN");
    }
}
