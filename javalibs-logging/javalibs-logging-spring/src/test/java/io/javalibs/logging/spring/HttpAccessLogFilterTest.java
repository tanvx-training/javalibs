package io.javalibs.logging.spring;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class HttpAccessLogFilterTest {

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

    private static HttpAccessLogFilter filter(AccessLogSettings settings) {
        return new HttpAccessLogFilter(settings, PrincipalResolver.DEFAULT,
                new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestMap() {
        return (Map<String, Object>) recorder.keyValue(LogFields.REQUEST);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseMap() {
        return (Map<String, Object>) recorder.keyValue(LogFields.RESPONSE);
    }

    @Test
    void logsMethodEndpointStatusAndLatency() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter(AccessLogSettings.defaults()).doFilter(request, response, (req, res) -> {
        });

        assertThat(requestMap())
                .containsEntry(LogFields.METHOD, "POST")
                .containsEntry(LogFields.ENDPOINT, "/api/login");
        assertThat(responseMap()).containsEntry(LogFields.STATUS, 200);
        assertThat((Long) responseMap().get(LogFields.LATENCY_MS)).isNotNegative();
        assertThat(recorder.single().getFormattedMessage()).startsWith("POST /api/login 200 ");
    }

    @Test
    void putsUserIdAndClientIpIntoTheMdcForTheDurationOfTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setUserPrincipal(() -> "user_12345");
        request.setRemoteAddr("192.168.1.10");
        AtomicReference<String> userIdDuringChain = new AtomicReference<>();
        AtomicReference<String> ipDuringChain = new AtomicReference<>();

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            userIdDuringChain.set(MDC.get(LogFields.MDC_USER_ID));
            ipDuringChain.set(MDC.get(LogFields.MDC_CLIENT_IP));
        });

        assertThat(userIdDuringChain.get()).isEqualTo("user_12345");
        assertThat(ipDuringChain.get()).isEqualTo("192.168.1.10");
        assertThat(MDC.get(LogFields.MDC_USER_ID)).isNull();
        assertThat(MDC.get(LogFields.MDC_CLIENT_IP)).isNull();
    }

    @Test
    void cleansTheMdcEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        request.setRemoteAddr("192.168.1.10");

        assertThatExceptionOfType(ServletException.class).isThrownBy(() ->
                filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(),
                        (req, res) -> {
                            throw new ServletException("boom");
                        }));

        assertThat(MDC.get(LogFields.MDC_CLIENT_IP)).isNull();
        assertThat(recorder.events()).hasSize(1);
    }

    @Test
    void ignoresSpoofedForwardedHeaderUnlessTheProxyIsTrusted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.setRemoteAddr("192.168.1.10");
        AtomicReference<String> ip = new AtomicReference<>();

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> ip.set(MDC.get(LogFields.MDC_CLIENT_IP)));

        assertThat(ip.get()).isEqualTo("192.168.1.10");
    }

    @Test
    void masksSecretsInTheQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/verify");
        request.setQueryString("user=alice&token=abc123");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(requestMap()).containsEntry(LogFields.ENDPOINT, "/api/verify?user=alice&token=********");
    }

    @Test
    void skipsExcludedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(recorder.events()).isEmpty();
    }

    @Test
    void raisesTheLevelToWarnForSlowRequests() throws Exception {
        AccessLogSettings slowIsOneMillisecond = new AccessLogSettings(
                false, null, false, 2048, null, false, 1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/slow");

        filter(slowIsOneMillisecond).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(recorder.single().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void omitsHeadersUnlessTheyAreExplicitlyEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("User-Agent", "Mozilla/5.0");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(requestMap()).doesNotContainKey(LogFields.HEADERS);
    }

    @Test
    void includesOnlyAllowlistedHeadersWhenEnabled() throws Exception {
        AccessLogSettings withHeaders = new AccessLogSettings(
                true, null, false, 2048, null, false, 0L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Cookie", "session=secret");

        filter(withHeaders).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) requestMap().get(LogFields.HEADERS);
        assertThat(headers).containsEntry("User-Agent", "Mozilla/5.0").doesNotContainKey("Cookie");
    }
}
