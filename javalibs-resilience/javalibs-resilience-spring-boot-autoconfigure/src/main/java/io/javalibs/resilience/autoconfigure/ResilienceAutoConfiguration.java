package io.javalibs.resilience.autoconfigure;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides Resilience4j registries seeded with the platform defaults from
 * {@code javalibs.resilience.*}. Every circuit breaker / retry / rate limiter
 * created from these registries inherits the defaults, so services get sane
 * protection without any per-service tuning.
 *
 * <p>Backs off when the application (or resilience4j-spring-boot3, if a team
 * chooses the annotation-driven style) provides its own registries.</p>
 */
@AutoConfiguration(afterName = {
        "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
        "io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration",
        "io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration"})
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

    /** Circuit breaker registry with platform defaults. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CircuitBreakerRegistry.class)
    static class CircuitBreakerConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
            ResilienceProperties.CircuitBreaker cb = properties.getCircuitBreaker();
            return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                    .failureRateThreshold(cb.getFailureRateThreshold())
                    .slowCallRateThreshold(cb.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(cb.getSlowCallDurationThreshold())
                    .slidingWindowSize(cb.getSlidingWindowSize())
                    .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                    .waitDurationInOpenState(cb.getWaitDurationInOpenState())
                    .permittedNumberOfCallsInHalfOpenState(cb.getPermittedNumberOfCallsInHalfOpenState())
                    .build());
        }
    }

    /** Retry registry with exponential backoff defaults. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RetryRegistry.class)
    static class RetryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RetryRegistry retryRegistry(ResilienceProperties properties) {
            ResilienceProperties.Retry retry = properties.getRetry();
            return RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(retry.getMaxAttempts())
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(
                            retry.getWaitDuration(), retry.getExponentialBackoffMultiplier()))
                    .build());
        }
    }

    /** Rate limiter registry with platform defaults. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RateLimiterRegistry.class)
    static class RateLimiterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RateLimiterRegistry rateLimiterRegistry(ResilienceProperties properties) {
            ResilienceProperties.RateLimiter limiter = properties.getRateLimiter();
            return RateLimiterRegistry.of(RateLimiterConfig.custom()
                    .limitForPeriod(limiter.getLimitForPeriod())
                    .limitRefreshPeriod(limiter.getLimitRefreshPeriod())
                    .timeoutDuration(limiter.getTimeoutDuration())
                    .build());
        }
    }
}
