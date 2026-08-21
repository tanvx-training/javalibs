package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import io.javalibs.logging.spring.AccessLogSettings;
import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.logging.spring.PrincipalResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the javalibs HTTP access log filter for servlet applications.
 *
 * <p>Enabled by default; switch it off with
 * {@code javalibs.logging.access.enabled=false}. The filter is ordered late in
 * the chain so that the status it records is the final one, and so that Spring
 * Security has already established the principal by the time {@code user_id} is
 * resolved.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "javalibs.logging.access", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AccessLogAutoConfiguration {

    /**
     * Supplies the default {@code user_id} resolver, reading the servlet
     * principal.
     *
     * @return the default resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public PrincipalResolver javalibsPrincipalResolver() {
        return PrincipalResolver.DEFAULT;
    }

    /**
     * Registers the access log filter configured from {@code javalibs.logging.*}.
     *
     * @param properties        the bound javalibs logging properties
     * @param principalResolver the resolver supplying {@code user_id}
     * @return the filter registration
     */
    @Bean
    @ConditionalOnMissingBean(HttpAccessLogFilter.class)
    public FilterRegistrationBean<HttpAccessLogFilter> javalibsHttpAccessLogFilter(
            LoggingProperties properties, PrincipalResolver principalResolver) {
        LoggingProperties.Access access = properties.access();
        AccessLogSettings settings = new AccessLogSettings(
                access.includeHeaders(), access.includedHeaders(), access.includeBody(),
                access.maxBodyLength(), access.excludedPaths(), access.trustProxy(),
                access.slowThresholdMs());

        LoggingProperties.Masking masking = properties.masking();
        SensitiveKeys keys = masking.enabled()
                ? SensitiveKeys.withAdditional(masking.keys()) : SensitiveKeys.none();
        SensitiveDataMasker masker = new SensitiveDataMasker(keys, masking.value());

        FilterRegistrationBean<HttpAccessLogFilter> registration = new FilterRegistrationBean<>(
                new HttpAccessLogFilter(settings, principalResolver, masker));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
