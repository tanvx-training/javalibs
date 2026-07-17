package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.GroupMembershipResolver;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.spring.CachingAuthzInvalidator;
import io.javalibs.authz.spring.CachingGrantResolver;
import io.javalibs.authz.spring.CachingGroupMembershipResolver;
import io.javalibs.authz.spring.PermissionChecker;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for the javalibs authz engine. Backs off when no
 * {@link GrantResolver}/{@link GroupMembershipResolver} beans exist (typically provided by
 * {@link AuthzJpaAutoConfiguration} or the application itself).
 */
@AutoConfiguration(after = AuthzJpaAutoConfiguration.class)
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AuthzProperties.class)
public class AuthzAutoConfiguration {

    /** Caffeine-backed caching decorators, marked primary so the evaluator uses them. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnProperty(prefix = "javalibs.authz.cache", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class CachingConfiguration {

        /**
         * Wraps the primary {@link GrantResolver} with a Caffeine cache keyed by subject.
         *
         * @param delegate the resolver whose results are cached
         * @param properties the bound {@code javalibs.authz.*} properties
         * @return the caching decorator, exposed as the primary {@link GrantResolver}
         */
        @Bean
        @Primary
        @ConditionalOnBean(GrantResolver.class)
        @ConditionalOnMissingBean(CachingGrantResolver.class)
        CachingGrantResolver javalibsCachingGrantResolver(GrantResolver delegate,
                AuthzProperties properties) {
            return new CachingGrantResolver(delegate,
                    properties.getCache().getTtl(), properties.getCache().getMaxSize());
        }

        /**
         * Wraps the primary {@link GroupMembershipResolver} with a Caffeine cache keyed by user.
         *
         * @param delegate the resolver whose results are cached
         * @param properties the bound {@code javalibs.authz.*} properties
         * @return the caching decorator, exposed as the primary {@link GroupMembershipResolver}
         */
        @Bean
        @Primary
        @ConditionalOnBean(GroupMembershipResolver.class)
        @ConditionalOnMissingBean(CachingGroupMembershipResolver.class)
        CachingGroupMembershipResolver javalibsCachingGroupMembershipResolver(
                GroupMembershipResolver delegate, AuthzProperties properties) {
            return new CachingGroupMembershipResolver(delegate,
                    properties.getCache().getTtl(), properties.getCache().getMaxSize());
        }

        /**
         * Exposes an {@link AuthzCacheInvalidator} that evicts both caching decorators, so
         * that {@link AuthzJpaAutoConfiguration}'s {@code AuthzManagementService} can refresh
         * caches after grant/membership mutations.
         *
         * @param grants the caching grant resolver to evict
         * @param groups the caching group membership resolver to evict
         * @return the composite cache invalidator
         */
        @Bean
        @ConditionalOnBean({CachingGrantResolver.class, CachingGroupMembershipResolver.class})
        @ConditionalOnMissingBean(AuthzCacheInvalidator.class)
        CachingAuthzInvalidator javalibsAuthzCacheInvalidator(CachingGrantResolver grants,
                CachingGroupMembershipResolver groups) {
            return new CachingAuthzInvalidator(grants, groups);
        }
    }

    /**
     * Creates the {@link PermissionEvaluator} once both an application-supplied (or JPA-backed)
     * {@link GrantResolver} and {@link GroupMembershipResolver} are available. When
     * {@link CachingConfiguration} is active, Spring injects the {@code @Primary} caching
     * decorators here instead of the raw resolvers.
     *
     * @param grantResolver the grant resolver to evaluate against
     * @param groupMembershipResolver the group membership resolver to evaluate against
     * @return the permission evaluator
     */
    @Bean
    @ConditionalOnBean({GrantResolver.class, GroupMembershipResolver.class})
    @ConditionalOnMissingBean
    public PermissionEvaluator javalibsPermissionEvaluator(GrantResolver grantResolver,
            GroupMembershipResolver groupMembershipResolver) {
        return new PermissionEvaluator(grantResolver, groupMembershipResolver);
    }

    /**
     * Creates the {@link PermissionChecker} facade over the {@link PermissionEvaluator}.
     *
     * @param evaluator the permission evaluator to delegate to
     * @return the permission checker
     */
    @Bean
    @ConditionalOnBean(PermissionEvaluator.class)
    @ConditionalOnMissingBean
    public PermissionChecker javalibsPermissionChecker(PermissionEvaluator evaluator) {
        return new PermissionChecker(evaluator);
    }
}
