package io.javalibs.logging.spring;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;

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

    @Test
    void restoresTheOuterMdcValueRatherThanClearingItWhenTheRequestFinishes() throws Exception {
        // Every other test in this file starts from an empty MDC and ends by
        // asserting null, which cannot tell "restored the previous value"
        // apart from "wiped it". A pooled thread that was already carrying
        // context for an outer unit of work must get that context back, not
        // an empty MDC, once this request is done.
        MDC.put(LogFields.MDC_USER_ID, "outer-user");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setUserPrincipal(() -> "inner-user");
        AtomicReference<String> userIdDuringChain = new AtomicReference<>();

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> userIdDuringChain.set(MDC.get(LogFields.MDC_USER_ID)));

        assertThat(userIdDuringChain.get()).isEqualTo("inner-user");
        assertThat(MDC.get(LogFields.MDC_USER_ID)).isEqualTo("outer-user");
    }

    @Test
    void restoresTheOuterMdcTagsRatherThanLeakingAnUnclosedInnerScope() throws Exception {
        // LogContext.tags() merges with whatever is already in the MDC_TAGS
        // entry, so a caller who forgets the try-with-resources (the compiler
        // does not warn -- the returned Scope is simply discarded) leaves the
        // inner tag stuck in the MDC. Without restoring MDC_TAGS here, that
        // stuck tag would leak into every subsequent request served by this
        // pooled thread, accumulating further with each miss.
        MDC.put(LogFields.MDC_TAGS, "outer");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            // Deliberately not closed, simulating the missed try-with-resources.
            LogContext.tags("inner");
        });

        assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("outer");
    }

    @Test
    void participatesInTheAsyncRedispatchInsteadOfBeingSkippedByIt() {
        // OncePerRequestFilter#shouldNotFilterAsyncDispatch() defaults to
        // true, which means the filter would never run again on the
        // container's async re-dispatch. That is precisely how an async
        // request's real status/latency never get logged and, once bodies are
        // captured, how ContentCachingResponseWrapper#copyBodyToResponse()
        // never gets called -- leaving the client with an empty response
        // forever. This assertion is the one-line guarantee that the filter
        // opted back in.
        HttpAccessLogFilter filter = filter(AccessLogSettings.defaults());
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }

    @Test
    void defersLoggingAndResponseDeliveryUntilAsyncProcessingActuallyCompletes() throws Exception {
        // Simulates, with the real Spring WebAsyncManager/DeferredResult
        // machinery (no servlet container involved), the two dispatches an
        // async request goes through: first the handler starts async work and
        // returns without writing anything; later, once the result is ready,
        // the container re-dispatches with DispatcherType.ASYNC and the
        // response is actually written. Logging the request on the first pass
        // would record a fake 200 status at ~0ms and, with includeBody on,
        // would flush an empty cached buffer -- exactly the bug being fixed.
        AccessLogSettings settings = new AccessLogSettings(false, null, true, 2048, null, false, 0L);
        HttpAccessLogFilter filter = new HttpAccessLogFilter(settings, PrincipalResolver.DEFAULT,
                new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.setAsyncWebRequest(new StandardServletAsyncWebRequest(request, response));
        DeferredResult<String> deferredResult = new DeferredResult<>();

        // A real container hands the async re-dispatch the exact same
        // (already-wrapped) request/response the handler was given on the
        // first pass -- not fresh ones -- so the test captures them here to
        // reuse below, the same way AsyncContext would.
        AtomicReference<HttpServletRequest> wrappedRequest = new AtomicReference<>();
        AtomicReference<HttpServletResponse> wrappedResponse = new AtomicReference<>();

        // First dispatch: the handler starts async processing and yields
        // without writing a status or body yet -- exactly what a controller
        // returning a DeferredResult/Callable/SSE emitter does.
        filter.doFilter(request, response, (req, res) -> {
            wrappedRequest.set((HttpServletRequest) req);
            wrappedResponse.set((HttpServletResponse) res);
            try {
                WebAsyncUtils.getAsyncManager(req).startDeferredResultProcessing(deferredResult);
            } catch (Exception ex) {
                throw new ServletException(ex);
            }
        });

        assertThat(WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted())
                .as("async processing is still in flight after the first dispatch returns")
                .isTrue();
        assertThat(recorder.events()).as("no log line before the request is actually done").isEmpty();
        assertThat(response.getContentAsString()).as("nothing delivered to the client yet").isEmpty();

        // The async work resolves, and the container re-dispatches the same
        // request/response so the result can actually be written out.
        deferredResult.setResult("done");
        // Per the Servlet spec, AsyncContext#dispatch() ends the "started"
        // state the instant it is called -- well before the redispatch
        // actually runs -- which is what lets a real container's second pass
        // through this filter see isAsyncStarted() == false. MockAsyncContext
        // does not simulate that timing on its own, so it is set explicitly
        // here, the same way the real container would have by this point.
        request.setAsyncStarted(false);
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(wrappedRequest.get(), wrappedResponse.get(), (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write("{\"done\":true}");
        });

        assertThat(recorder.events())
                .as("exactly one access log line, emitted once the request is truly finished")
                .hasSize(1);
        assertThat(response.getContentAsString()).isEqualTo("{\"done\":true}");
    }
}
