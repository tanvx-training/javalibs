package io.javalibs.observability.autoconfigure;

import io.javalibs.observability.spring.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration registering the {@link CorrelationIdFilter} for servlet
 * web applications.
 *
 * <p>The filter is registered close to {@link Ordered#HIGHEST_PRECEDENCE} so
 * the correlation id is available in the MDC before request logging filters
 * from other libraries run. It can be disabled with
 * {@code javalibs.observability.correlation.enabled=false} and the header can
 * be changed with {@code javalibs.observability.correlation.header}.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.observability.correlation", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class CorrelationIdWebAutoConfiguration {

    /**
     * Registers the correlation id filter on all URL patterns with an order
     * of {@code Ordered.HIGHEST_PRECEDENCE + 10}.
     *
     * @param properties the observability properties supplying the header name
     * @return the filter registration
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            ObservabilityProperties properties) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(properties.getCorrelation().getHeader()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdFilter");
        return registration;
    }
}
