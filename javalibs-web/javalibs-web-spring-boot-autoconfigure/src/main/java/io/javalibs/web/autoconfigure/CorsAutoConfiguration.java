package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.CorsSupport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Auto-configuration registering a {@link CorsFilter} built from
 * {@code javalibs.web.cors.*} properties.
 *
 * <p>Opt-in only: the filter is registered exclusively when
 * {@code javalibs.web.cors.enabled=true}. It runs with high precedence so CORS headers
 * are applied before any other filter produces a response.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebProperties.class)
@ConditionalOnProperty(prefix = "javalibs.web.cors", name = "enabled", havingValue = "true")
public class CorsAutoConfiguration {

    /**
     * Registers the CORS filter configured from {@code javalibs.web.cors.*}.
     *
     * @param properties the bound javalibs web properties
     * @return the filter registration
     */
    @Bean
    @ConditionalOnMissingBean(CorsFilter.class)
    public FilterRegistrationBean<CorsFilter> javalibsCorsFilter(WebProperties properties) {
        WebProperties.Cors cors = properties.cors();
        CorsConfiguration configuration = CorsSupport.build(
                cors.allowedOrigins(),
                cors.allowedMethods(),
                cors.allowedHeaders(),
                cors.exposedHeaders(),
                cors.allowCredentials(),
                cors.maxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(cors.path(), configuration);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
