package io.javalibs.security.autoconfigure;

import io.javalibs.security.spring.AccessDeniedExceptionAdvice;
import io.javalibs.security.spring.CurrentUser;
import io.javalibs.security.spring.CurrentUserArgumentResolver;
import io.javalibs.security.spring.RequireRole;
import io.javalibs.security.spring.RequireRoleInterceptor;
import io.javalibs.security.spring.RestAccessDeniedHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@AutoConfiguration(after = JavalibsSecurityAutoConfiguration.class)
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

    /**
     * Registers the advice that renders denials raised by {@link RequireRoleInterceptor} (and
     * the authz module's permission interceptor) as the standard 403 body.
     *
     * <p>Without it, an application that also uses javalibs-web has its
     * {@code GlobalExceptionHandler} catch-all swallow the {@code AccessDeniedException}
     * during MVC dispatch and return 500 — see {@link AccessDeniedExceptionAdvice} for why the
     * security filter chain never gets a chance to handle it.</p>
     *
     * @param accessDeniedHandler the handler rendering the 403 body, shared with the filter chain
     * @return the advice bean
     */
    @Bean
    @ConditionalOnBean(RestAccessDeniedHandler.class)
    @ConditionalOnMissingBean
    public AccessDeniedExceptionAdvice javalibsAccessDeniedExceptionAdvice(
            RestAccessDeniedHandler accessDeniedHandler) {
        return new AccessDeniedExceptionAdvice(accessDeniedHandler);
    }
}
