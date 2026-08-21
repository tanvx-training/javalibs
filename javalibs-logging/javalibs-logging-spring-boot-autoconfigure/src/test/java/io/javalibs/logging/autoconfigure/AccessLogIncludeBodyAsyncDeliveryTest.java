package io.javalibs.logging.autoconfigure;

import java.nio.charset.StandardCharsets;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end exercise of {@code access.include-body} through a real embedded
 * servlet container and a real HTTP client, rather than the hand-rolled
 * {@code MockHttpServletRequest}/{@code MockHttpServletResponse} simulation
 * used by {@code HttpAccessLogFilterTest} in {@code javalibs-logging-spring}.
 *
 * <p><strong>Finding (2026-08-22):</strong> the synchronous case works as
 * documented — see {@link #synchronousEndpointDeliversTheFullBodyWithIncludeBodyOn()}.
 * The async case does not: {@link #asyncEndpointFailsToDeliverTheBodyWithIncludeBodyOn_KNOWN_BUG()}
 * reproduces, against a real Tomcat instance, a request to a
 * {@link DeferredResult}-returning endpoint with
 * {@code javalibs.logging.access.include-body=true} coming back with
 * {@code Content-Length: 0} and an empty body — every time, not
 * intermittently, and unaffected by adding a delay before
 * {@code DeferredResult#setResult} to rule out a timing race. This holds even
 * though the mocked unit test
 * {@code HttpAccessLogFilterTest#defersLoggingAndResponseDeliveryUntilAsyncProcessingActuallyCompletes()}
 * passes and asserts the opposite (a fully delivered body) — the mock request/
 * response simulation does not reproduce whatever a real container does
 * differently with the wrapped {@code ContentCachingResponseWrapper} across
 * the {@code AsyncContext} re-dispatch. The test is left in the source tree,
 * disabled, as a reproduction case for follow-up; it is not part of this
 * change's fix scope (see the final fix report for 2026-08-22).</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = AccessLogIncludeBodyAsyncDeliveryTest.TestApplication.class)
@TestPropertySource(properties = {
        "javalibs.logging.access.include-body=true",
        "javalibs.logging.access.enabled=true"
})
class AccessLogIncludeBodyAsyncDeliveryTest {

    private static final String EXPECTED_BODY = "{\"message\":\"hello\"}";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void synchronousEndpointDeliversTheFullBodyWithIncludeBodyOn() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity("/sync/greeting", byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    @Disabled("""
            KNOWN BUG reproduced against a real embedded Tomcat, not covered by the mocked \
            HttpAccessLogFilterTest#defersLoggingAndResponseDeliveryUntilAsyncProcessingActuallyCompletes(). \
            With javalibs.logging.access.include-body=true, a DeferredResult-returning endpoint comes back \
            to the client with Content-Length: 0 and an empty body -- reproducible every run, including \
            with an added delay before DeferredResult#setResult to rule out a timing race. Left disabled \
            rather than fixed here: out of scope for the A1-A3 code changes this branch's final-fix pass \
            was authorized to make. See the 2026-08-22 final fix report for follow-up.""")
    void asyncEndpointFailsToDeliverTheBodyWithIncludeBodyOn_KNOWN_BUG() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity("/async/greeting", byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo(EXPECTED_BODY);
    }

    @EnableAutoConfiguration
    @Configuration(proxyBeanMethods = false)
    static class TestApplication {

        @RestController
        static class GreetingController {

            @GetMapping(path = "/sync/greeting", produces = "application/json")
            String syncGreeting() {
                return EXPECTED_BODY;
            }

            /**
             * Mirrors a controller backed by a downstream call or a message
             * broker reply -- the case that needs
             * {@link HttpAccessLogFilter}'s two-dispatch handling to deliver
             * anything to the client at all.
             */
            @GetMapping(path = "/async/greeting", produces = "application/json")
            DeferredResult<String> asyncGreeting() {
                DeferredResult<String> result = new DeferredResult<>();
                Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    result.setResult(EXPECTED_BODY);
                });
                return result;
            }
        }
    }
}
