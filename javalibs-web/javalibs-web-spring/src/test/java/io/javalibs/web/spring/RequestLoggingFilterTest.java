package io.javalibs.web.spring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestLoggingFilter} using mock servlet objects.
 */
class RequestLoggingFilterTest {

    @Test
    void proceedsThroughChainWithDefaults() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/things");
        request.setQueryString("page=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new EchoServlet());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void payloadLoggingDoesNotBreakResponseBody() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(true, 2048, List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/things");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"widget\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new EchoServlet());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    void payloadLoggingHandlesBinaryResponseWithoutCorruption() throws Exception {
        byte[] binary = {0x00, 0x01, 0x02, (byte) 0xFF};
        RequestLoggingFilter filter = new RequestLoggingFilter(true, 16, List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/binary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                res.setContentType("application/octet-stream");
                res.getOutputStream().write(binary);
            }
        });

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsByteArray()).isEqualTo(binary);
    }

    @Test
    void excludedPathsAreNotFiltered() {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/things");

        assertThat(filter.shouldNotFilter(actuator)).isTrue();
        assertThat(filter.shouldNotFilter(api)).isFalse();
    }

    @Test
    void customExcludedPathsArehonored() {
        RequestLoggingFilter filter = new RequestLoggingFilter(false, 2048, List.of("/internal/**"));

        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/internal/jobs");
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(internal)).isTrue();
        assertThat(filter.shouldNotFilter(actuator)).isFalse();
    }

    /**
     * Simple servlet writing a small JSON body.
     */
    private static final class EchoServlet extends HttpServlet {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse res)
                throws ServletException, IOException {
            res.setContentType("application/json");
            res.getWriter().write("{\"ok\":true}");
        }
    }
}
