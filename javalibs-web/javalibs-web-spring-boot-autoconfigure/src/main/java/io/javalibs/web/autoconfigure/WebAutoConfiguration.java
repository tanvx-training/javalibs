package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration registering the javalibs {@link GlobalExceptionHandler} for servlet
 * web applications.
 *
 * <p>The handler backs off when the application defines its own
 * {@link GlobalExceptionHandler} bean, and can be disabled with
 * {@code javalibs.web.exception-handler.enabled=false}.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebProperties.class)
public class WebAutoConfiguration {

    /**
     * Registers the global exception handler unless the application provides its own.
     *
     * @return the javalibs global exception handler
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "javalibs.web.exception-handler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public GlobalExceptionHandler javalibsGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
