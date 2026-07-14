package io.javalibs.security.autoconfigure;

import io.javalibs.security.spring.CurrentUser;
import io.javalibs.security.spring.CurrentUserArgumentResolver;
import io.javalibs.security.spring.RequireRole;
import io.javalibs.security.spring.RequireRoleInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration registering the javalibs security Web MVC helpers:
 * the {@link RequireRoleInterceptor} enforcing {@link RequireRole @RequireRole} and the
 * {@link CurrentUserArgumentResolver} backing {@link CurrentUser @CurrentUser} /
 * {@code UserContext} controller parameters.
 *
 * <p>Gated by the same {@code javalibs.security.enabled} flag as
 * {@link JavalibsSecurityAutoConfiguration}.</p>
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.security", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SecurityWebMvcAutoConfiguration {

    /**
     * Registers the interceptor and argument resolver with Spring MVC.
     *
     * @return the {@link WebMvcConfigurer} contributing both components
     */
    @Bean
    public WebMvcConfigurer javalibsSecurityWebMvcConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequireRoleInterceptor());
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
