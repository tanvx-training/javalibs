package io.javalibs.resilience.autoconfigure;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ResilienceAutoConfiguration.class,
                    ResilienceRestAutoConfiguration.class));

    @Test
    void registriesCreatedWithPlatformDefaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(RetryRegistry.class);
            assertThat(context).hasSingleBean(RateLimiterRegistry.class);

            var cbConfig = context.getBean(CircuitBreakerRegistry.class).getDefaultConfig();
            assertThat(cbConfig.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(cbConfig.getSlidingWindowSize()).isEqualTo(20);
            assertThat(cbConfig.getMinimumNumberOfCalls()).isEqualTo(10);

            var retryConfig = context.getBean(RetryRegistry.class).getDefaultConfig();
            assertThat(retryConfig.getMaxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void propertyOverridesApply() {
        runner.withPropertyValues(
                        "javalibs.resilience.circuit-breaker.failure-rate-threshold=25",
                        "javalibs.resilience.circuit-breaker.wait-duration-in-open-state=10s",
                        "javalibs.resilience.retry.max-attempts=5")
                .run(context -> {
                    var cbConfig = context.getBean(CircuitBreakerRegistry.class).getDefaultConfig();
                    assertThat(cbConfig.getFailureRateThreshold()).isEqualTo(25f);
                    assertThat(context.getBean(RetryRegistry.class).getDefaultConfig()
                            .getMaxAttempts()).isEqualTo(5);
                });
    }

    @Test
    void userRegistryBacksOff() {
        runner.withUserConfiguration(UserRegistry.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
                    assertThat(context.getBean(CircuitBreakerRegistry.class).getDefaultConfig()
                            .getSlidingWindowSize()).isEqualTo(99);
                });
    }

    @Configuration
    static class UserRegistry {
        @Bean
        CircuitBreakerRegistry customRegistry() {
            return CircuitBreakerRegistry.of(
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(99)
                            .build());
        }
    }

    @Test
    void restCustomizerIsOptIn() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RestClientCustomizer.class));

        runner.withPropertyValues("javalibs.resilience.rest.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClientCustomizer.class);
                    // The customizer applies cleanly to a builder.
                    RestClient.Builder builder = RestClient.builder();
                    context.getBean(RestClientCustomizer.class).customize(builder);
                    assertThat(builder.build()).isNotNull();
                    assertThat(context.getBean(CircuitBreakerRegistry.class)
                            .getAllCircuitBreakers())
                            .anySatisfy(cb -> assertThat(cb.getName()).isEqualTo("rest-client"));
                });
    }

    @Test
    void rateLimiterDefaultsApplied() {
        runner.run(context -> {
            var config = context.getBean(RateLimiterRegistry.class).getDefaultConfig();
            assertThat(config.getLimitForPeriod()).isEqualTo(50);
            assertThat(config.getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(1));
        });
    }
}
