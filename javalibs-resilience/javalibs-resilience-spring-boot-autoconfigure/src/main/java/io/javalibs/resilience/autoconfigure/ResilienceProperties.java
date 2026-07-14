package io.javalibs.resilience.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Platform defaults for Resilience4j ({@code javalibs.resilience.*}). These
 * values seed the default config of the auto-configured registries; individual
 * breakers/retries created from a registry inherit them.
 */
@ConfigurationProperties(prefix = "javalibs.resilience")
public class ResilienceProperties {

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Retry retry = new Retry();
    private final RateLimiter rateLimiter = new RateLimiter();
    private final Rest rest = new Rest();

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public Retry getRetry() {
        return retry;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public Rest getRest() {
        return rest;
    }

    /** Circuit breaker defaults ({@code javalibs.resilience.circuit-breaker.*}). */
    public static class CircuitBreaker {

        /** Failure percentage at which the breaker opens. */
        private float failureRateThreshold = 50f;

        /** Slow-call percentage at which the breaker opens. */
        private float slowCallRateThreshold = 100f;

        /** Calls slower than this count as slow. */
        private Duration slowCallDurationThreshold = Duration.ofSeconds(2);

        /** Size of the sliding window used to compute rates. */
        private int slidingWindowSize = 20;

        /** Minimum calls before rates are evaluated. */
        private int minimumNumberOfCalls = 10;

        /** How long the breaker stays OPEN before probing again. */
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);

        /** Probe calls allowed in HALF_OPEN state. */
        private int permittedNumberOfCallsInHalfOpenState = 5;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permitted) {
            this.permittedNumberOfCallsInHalfOpenState = permitted;
        }
    }

    /** Retry defaults ({@code javalibs.resilience.retry.*}). */
    public static class Retry {

        /** Total attempts including the first call. */
        private int maxAttempts = 3;

        /** Initial wait between attempts. */
        private Duration waitDuration = Duration.ofMillis(500);

        /** Multiplier applied to the wait after each attempt (exponential backoff). */
        private double exponentialBackoffMultiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }

        public double getExponentialBackoffMultiplier() {
            return exponentialBackoffMultiplier;
        }

        public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) {
            this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
        }
    }

    /** Rate limiter defaults ({@code javalibs.resilience.rate-limiter.*}). */
    public static class RateLimiter {

        /** Permits per refresh period. */
        private int limitForPeriod = 50;

        /** Refresh period. */
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);

        /** How long a call waits for a permit (zero = fail fast). */
        private Duration timeoutDuration = Duration.ZERO;

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getLimitRefreshPeriod() {
            return limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }
    }

    /** Outbound HTTP protection ({@code javalibs.resilience.rest.*}). */
    public static class Rest {

        /** Opt-in: protect every RestClient.Builder with a circuit breaker. */
        private boolean enabled = false;

        /** Name of the circuit breaker used for RestClient calls. */
        private String circuitBreakerName = "rest-client";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCircuitBreakerName() {
            return circuitBreakerName;
        }

        public void setCircuitBreakerName(String circuitBreakerName) {
            this.circuitBreakerName = circuitBreakerName;
        }
    }
}
