package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.spring.PermissionChecker;
import io.javalibs.authz.spring.RequirePermissionInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@code @RequirePermission} interceptor with Spring MVC. Applies only to
 * servlet web applications and only once a {@link PermissionChecker} bean has been created
 * by {@link AuthzAutoConfiguration}.
 */
@AutoConfiguration(after = AuthzAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(PermissionChecker.class)
public class AuthzWebMvcAutoConfiguration {

    /**
     * Registers a {@link RequirePermissionInterceptor} backed by the given
     * {@link PermissionChecker} with Spring MVC's interceptor chain.
     *
     * @param permissionChecker the permission checker used to evaluate {@code @RequirePermission}
     * @return the MVC configurer registering the interceptor
     */
    @Bean
    public WebMvcConfigurer javalibsAuthzWebMvcConfigurer(PermissionChecker permissionChecker) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequirePermissionInterceptor(permissionChecker));
            }
        };
    }
}
