package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.GroupMembershipResolver;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.spring.CachingGrantResolver;
import io.javalibs.authz.spring.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthzAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class ResolverConfiguration {
        @Bean
        GrantResolver grantResolver() {
            return subject -> List.of();
        }
        @Bean
        GroupMembershipResolver groupMembershipResolver() {
            return userId -> Set.of();
        }
    }

    @Test
    void createsEvaluatorCheckerAndCachingDecoratorsWhenResolversPresent() {
        runner.withUserConfiguration(ResolverConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PermissionEvaluator.class);
            assertThat(context).hasSingleBean(PermissionChecker.class);
            assertThat(context).hasSingleBean(CachingGrantResolver.class);
        });
    }

    @Test
    void skipsCachingWhenDisabled() {
        runner.withUserConfiguration(ResolverConfiguration.class)
                .withPropertyValues("javalibs.authz.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionEvaluator.class);
                    assertThat(context).doesNotHaveBean(CachingGrantResolver.class);
                });
    }

    @Test
    void backsOffEntirelyWhenDisabled() {
        runner.withUserConfiguration(ResolverConfiguration.class)
                .withPropertyValues("javalibs.authz.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PermissionEvaluator.class));
    }

    @Test
    void backsOffWhenNoResolversDefined() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PermissionEvaluator.class));
    }
}
