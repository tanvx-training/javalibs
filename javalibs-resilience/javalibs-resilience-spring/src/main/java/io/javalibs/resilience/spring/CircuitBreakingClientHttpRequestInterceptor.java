package io.javalibs.resilience.spring;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Protects outbound HTTP calls with a Resilience4j {@link CircuitBreaker}.
 *
 * <p>While the breaker is CLOSED, calls pass through and their outcome is
 * recorded (I/O errors and 5xx responses count as failures). When the failure
 * rate crosses the configured threshold the breaker OPENs and subsequent calls
 * fail immediately with an {@link IOException} — without touching the sick
 * downstream service, giving it room to recover and protecting this service's
 * threads from piling up on a dead dependency (the root of cascading failures).
 *
 * <p>Combine with a retry interceptor by registering the circuit breaker
 * <em>closest to the wire</em> (last in the interceptor chain) so each retry
 * attempt is individually recorded.
 */
public class CircuitBreakingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakingClientHttpRequestInterceptor.class);

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakingClientHttpRequestInterceptor(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("Circuit breaker '{}' is OPEN; {} {} not attempted",
                    circuitBreaker.getName(), request.getMethod(), request.getURI());
            throw new IOException("Circuit breaker '" + circuitBreaker.getName()
                    + "' is open; request to " + request.getURI() + " was not attempted");
        }
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long elapsed = System.nanoTime() - start;
            if (response.getStatusCode().is5xxServerError()) {
                circuitBreaker.onError(elapsed, TimeUnit.NANOSECONDS,
                        new IOException("HTTP " + response.getStatusCode().value()
                                + " from " + request.getURI()));
            } else {
                circuitBreaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
            }
            return response;
        } catch (IOException | RuntimeException e) {
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            throw e;
        }
    }
}
