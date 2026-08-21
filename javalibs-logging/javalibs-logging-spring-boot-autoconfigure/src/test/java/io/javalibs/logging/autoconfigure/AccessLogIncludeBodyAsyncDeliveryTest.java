package io.javalibs.logging.autoconfigure;

import java.nio.charset.StandardCharsets;

import io.javalibs.logging.spring.HttpAccessLogFilter;
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
 * <p>The async case needs a real container because the bug it guards against
 * only shows up there: on the {@code ASYNC} re-dispatch, Spring's {@code
 * StandardServletAsyncWebRequest} hands the filter back a response wrapped in
 * its own {@code LifecycleHttpServletResponse}, one layer outside the {@link
 * org.springframework.web.util.ContentCachingResponseWrapper}
 * {@link HttpAccessLogFilter} created on the first pass. The mocked unit test
 * does not reproduce that extra layer, so this test is the real evidence that
 * {@link HttpAccessLogFilter} unwraps it correctly via {@code
 * WebUtils.getNativeResponse(...)} and delivers the full body to the
 * client.</p>
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
    void asyncEndpointDeliversTheFullBodyWithIncludeBodyOn() {
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
