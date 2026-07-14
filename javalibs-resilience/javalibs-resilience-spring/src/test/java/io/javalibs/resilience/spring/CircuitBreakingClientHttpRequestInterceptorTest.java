package io.javalibs.resilience.spring;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class CircuitBreakingClientHttpRequestInterceptorTest {

    private CircuitBreaker smallBreaker() {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build());
    }

    private MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create("http://backend/orders"));
    }

    @Test
    void passesThroughAndRecordsSuccess() throws Exception {
        CircuitBreaker breaker = smallBreaker();
        var interceptor = new CircuitBreakingClientHttpRequestInterceptor(breaker);
        ClientHttpRequestExecution execution =
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        var response = interceptor.intercept(request(), new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void opensAfterServerErrorsAndShortCircuits() throws Exception {
        CircuitBreaker breaker = smallBreaker();
        var interceptor = new CircuitBreakingClientHttpRequestInterceptor(breaker);
        AtomicInteger backendCalls = new AtomicInteger();
        ClientHttpRequestExecution failing = (req, body) -> {
            backendCalls.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR);
        };

        interceptor.intercept(request(), new byte[0], failing);
        interceptor.intercept(request(), new byte[0], failing);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatIOException()
                .isThrownBy(() -> interceptor.intercept(request(), new byte[0], failing))
                .withMessageContaining("is open");
        // The downstream service was NOT called while the breaker is open.
        assertThat(backendCalls).hasValue(2);
    }

    @Test
    void ioExceptionsCountAsFailuresAndPropagate() {
        CircuitBreaker breaker = smallBreaker();
        var interceptor = new CircuitBreakingClientHttpRequestInterceptor(breaker);
        ClientHttpRequestExecution broken = (req, body) -> {
            throw new IOException("connection refused");
        };

        assertThatIOException()
                .isThrownBy(() -> interceptor.intercept(request(), new byte[0], broken))
                .withMessageContaining("connection refused");
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }
}
