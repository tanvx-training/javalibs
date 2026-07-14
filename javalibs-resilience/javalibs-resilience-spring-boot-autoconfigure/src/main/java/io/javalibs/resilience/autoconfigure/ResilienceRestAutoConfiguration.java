package io.javalibs.resilience.autoconfigure;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.javalibs.resilience.spring.CircuitBreakingClientHttpRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Opt-in protection of outbound HTTP calls
 * ({@code javalibs.resilience.rest.enabled=true}): every auto-configured
 * {@link RestClient.Builder} gets a {@link CircuitBreakingClientHttpRequestInterceptor}
 * backed by the shared circuit breaker registry. Pairs naturally with the
 * retrying builder from javalibs-datahub.
 */
@AutoConfiguration(after = ResilienceAutoConfiguration.class)
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class, CircuitBreakerRegistry.class})
@ConditionalOnProperty(prefix = "javalibs.resilience.rest", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceRestAutoConfiguration {

    @Bean
    @ConditionalOnBean(CircuitBreakerRegistry.class)
    @ConditionalOnMissingBean(name = "javalibsCircuitBreakingRestClientCustomizer")
    public RestClientCustomizer javalibsCircuitBreakingRestClientCustomizer(
            CircuitBreakerRegistry registry, ResilienceProperties properties) {
        var interceptor = new CircuitBreakingClientHttpRequestInterceptor(
                registry.circuitBreaker(properties.getRest().getCircuitBreakerName()));
        return builder -> builder.requestInterceptor(interceptor);
    }
}
