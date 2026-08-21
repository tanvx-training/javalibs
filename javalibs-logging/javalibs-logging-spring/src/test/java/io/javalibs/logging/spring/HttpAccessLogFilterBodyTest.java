package io.javalibs.logging.spring;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAccessLogFilterBodyTest {

    private AccessLogRecorder recorder;

    @BeforeEach
    void attachRecorder() {
        recorder = AccessLogRecorder.attach();
    }

    @AfterEach
    void cleanUp() {
        recorder.detach();
        MDC.clear();
    }

    private static HttpAccessLogFilter filterWithBodies(int maxBodyLength) {
        AccessLogSettings settings =
                new AccessLogSettings(false, null, true, maxBodyLength, null, false, 0L);
        return new HttpAccessLogFilter(settings, PrincipalResolver.DEFAULT,
                new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK));
    }

    @SuppressWarnings("unchecked")
    private Object requestBody() {
        return ((Map<String, Object>) recorder.keyValue(LogFields.REQUEST)).get(LogFields.BODY);
    }

    @SuppressWarnings("unchecked")
    private Object responseBody() {
        return ((Map<String, Object>) recorder.keyValue(LogFields.RESPONSE)).get(LogFields.RESPONSE_BODY);
    }

    @Test
    void parsesAJsonRequestBodyIntoAnObject() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"hunter2\"}"
                .getBytes(StandardCharsets.UTF_8));

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestBody();
        assertThat(body).containsEntry("email", "user@example.com").containsEntry("password", "hunter2");
    }

    @Test
    void masksSecretsInAFormEncodedBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("user=alice&password=hunter2".getBytes(StandardCharsets.UTF_8));

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isEqualTo("user=alice&password=********");
    }

    @Test
    void capturesTheResponseBodyAndStillDeliversItToTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithBodies(2048).doFilter(request, response, (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":true}");
        });

        assertThat(responseBody()).isInstanceOf(Map.class);
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    void neverLogsBinaryContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[] {0x00, 0x01, 0x02, 0x03});

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isNull();
    }

    @Test
    void keepsATruncatedJsonBodyAsAStringRatherThanDroppingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"note\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}".getBytes(StandardCharsets.UTF_8));

        filterWithBodies(12).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isInstanceOf(String.class);
        assertThat((String) requestBody()).hasSizeLessThanOrEqualTo(12);
    }

    @Test
    void omitsBodiesEntirelyWhenCaptureIsOff() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"hunter2\"}".getBytes(StandardCharsets.UTF_8));

        HttpAccessLogFilter filter = new HttpAccessLogFilter(AccessLogSettings.defaults(),
                PrincipalResolver.DEFAULT, null);
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = (Map<String, Object>) recorder.keyValue(LogFields.REQUEST);
        assertThat(requestMap).doesNotContainKey(LogFields.BODY);
    }

    @Test
    void stillDeliversTheResponseWhenTheChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        List<Throwable> thrown = new java.util.ArrayList<>();
        try {
            filterWithBodies(2048).doFilter(request, response, (req, res) -> {
                res.setContentType("application/json");
                res.getWriter().write("{\"partial\":true}");
                throw new IllegalStateException("boom");
            });
        } catch (Exception ex) {
            thrown.add(ex);
        }

        assertThat(thrown).hasSize(1);
        assertThat(response.getContentAsString()).isEqualTo("{\"partial\":true}");
    }
}
