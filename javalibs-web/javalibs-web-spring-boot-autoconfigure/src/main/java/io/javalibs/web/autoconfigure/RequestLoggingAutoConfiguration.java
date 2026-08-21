package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.RequestLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration registering the javalibs {@link RequestLoggingFilter} for servlet
 * web applications.
 *
 * <p>Enabled by default; disable with {@code javalibs.web.logging.enabled=false}. The
 * filter runs late in the chain (close to the handler) so the logged status reflects the
 * final response.</p>
 *
 * <p>It backs off entirely when {@code javalibs-logging-spring} is on the
 * classpath: that module's {@code HttpAccessLogFilter} supersedes this one, and
 * registering both would log every request twice. The class is referenced by
 * name so that javalibs-web keeps no dependency on javalibs-logging.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebProperties.class)
@ConditionalOnProperty(prefix = "javalibs.web.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingClass("io.javalibs.logging.spring.HttpAccessLogFilter")
public class RequestLoggingAutoConfiguration {

    /**
     * Registers the request logging filter configured from {@code javalibs.web.logging.*}.
     *
     * @param properties the bound javalibs web properties
     * @return the filter registration
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RequestLoggingFilter> javalibsRequestLoggingFilter(WebProperties properties) {
        WebProperties.Logging logging = properties.logging();
        RequestLoggingFilter filter = new RequestLoggingFilter(
                logging.includePayload(), logging.maxPayloadLength(), logging.excludedPaths());
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
