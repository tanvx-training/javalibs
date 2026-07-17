package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.GroupMembershipResolver;
import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.jpa.AuthzGroupMemberRepository;
import io.javalibs.authz.jpa.AuthzGroupRepository;
import io.javalibs.authz.jpa.AuthzManagementService;
import io.javalibs.authz.jpa.AuthzRefreshTokenRepository;
import io.javalibs.authz.jpa.AuthzRoleGrantRepository;
import io.javalibs.authz.jpa.AuthzRoleRepository;
import io.javalibs.authz.jpa.AuthzUserEntity;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.authz.jpa.JpaGrantResolver;
import io.javalibs.authz.jpa.JpaGroupMembershipResolver;
import io.javalibs.authz.jpa.issuer.JpaCredentialsStore;
import io.javalibs.authz.jpa.issuer.JpaRefreshTokenStore;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.RefreshTokenStore;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Auto-configuration wiring the default JPA persistence of javalibs-authz: entity scanning,
 * repositories, the JPA resolvers, the management service and the bundled Flyway migrations.
 * Backs off when javalibs-authz-jpa or Spring Data JPA is not on the classpath.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class,
        after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({JpaGrantResolver.class, JpaRepository.class})
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "javalibs.authz.jpa", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AuthzProperties.class)
@EntityScan(basePackageClasses = AuthzUserEntity.class)
@EnableJpaRepositories(basePackageClasses = AuthzUserRepository.class)
public class AuthzJpaAutoConfiguration {

    /**
     * Appends the bundled migration location so Flyway creates the {@code authz_*} tables.
     * Runs before {@link FlywayAutoConfiguration} so the location is present when Flyway
     * migrates the schema.
     *
     * @return the customizer appending {@code classpath:db/migration/javalibs-authz}
     */
    @Bean
    @ConditionalOnClass(Flyway.class)
    @ConditionalOnProperty(prefix = "javalibs.authz.jpa", name = "apply-migrations",
            havingValue = "true", matchIfMissing = true)
    public FlywayConfigurationCustomizer javalibsAuthzFlywayCustomizer() {
        return configuration -> {
            List<String> locations = new ArrayList<>(Arrays.stream(configuration.getLocations())
                    .map(Object::toString).toList());
            String authzLocation = "classpath:db/migration/javalibs-authz";
            if (!locations.contains(authzLocation)) {
                locations.add(authzLocation);
                configuration.locations(locations.toArray(String[]::new));
            }
        };
    }

    /**
     * Exposes the default JPA-backed {@link GrantResolver}, unless the application already
     * supplies its own.
     *
     * @param grantRepository the repository over {@code authz_role_grant} rows
     * @return the JPA grant resolver
     */
    @Bean
    @ConditionalOnMissingBean(GrantResolver.class)
    public JpaGrantResolver javalibsJpaGrantResolver(AuthzRoleGrantRepository grantRepository) {
        return new JpaGrantResolver(grantRepository);
    }

    /**
     * Exposes the default JPA-backed {@link GroupMembershipResolver}, unless the application
     * already supplies its own.
     *
     * @param memberRepository the repository over {@code authz_group_member} rows
     * @return the JPA group membership resolver
     */
    @Bean
    @ConditionalOnMissingBean(GroupMembershipResolver.class)
    public JpaGroupMembershipResolver javalibsJpaGroupMembershipResolver(
            AuthzGroupMemberRepository memberRepository) {
        return new JpaGroupMembershipResolver(memberRepository);
    }

    /**
     * Exposes the administrative {@link AuthzManagementService} over the authz repositories.
     * The optional {@link PermissionCatalog} and {@link AuthzCacheInvalidator} collaborators
     * are resolved lazily via {@link ObjectProvider}: the catalog is provided by
     * {@code javalibs-authz-core} when configured with role definitions, and the invalidator
     * is provided by {@link AuthzAutoConfiguration.CachingConfiguration} when caching is
     * enabled. When a catalog is present, every stored role is validated against it at
     * startup so the application fails fast on an unknown permission code.
     *
     * @param users repository for {@code authz_user} rows
     * @param groups repository for {@code authz_group} rows
     * @param members repository for {@code authz_group_member} rows
     * @param roles repository for {@code authz_role} rows
     * @param grants repository for {@code authz_role_grant} rows
     * @param catalog the optional permission catalog used to validate role permission codes
     * @param invalidator the optional cache invalidator used to evict caches after mutations
     * @return the management service, already validated against the catalog when present
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthzManagementService javalibsAuthzManagementService(
            AuthzUserRepository users, AuthzGroupRepository groups,
            AuthzGroupMemberRepository members, AuthzRoleRepository roles,
            AuthzRoleGrantRepository grants,
            ObjectProvider<PermissionCatalog> catalog,
            ObjectProvider<AuthzCacheInvalidator> invalidator) {
        PermissionCatalog catalogIfAvailable = catalog.getIfAvailable();
        AuthzManagementService service = new AuthzManagementService(users, groups, members,
                roles, grants, catalogIfAvailable, invalidator.getIfAvailable());
        // Fail fast at startup when stored roles reference unknown permission codes.
        if (catalogIfAvailable != null) {
            // Called on the raw instance, before proxying: @Transactional has no effect here.
            // Safe because findAllWithPermissions() is a self-transactional Spring Data query.
            service.validateRolesAgainstCatalog();
        }
        return service;
    }

    /** Wires the issuer stores when javalibs-security-issuer is on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CredentialsStore.class)
    static class IssuerStoreConfiguration {

        /**
         * Exposes the default JPA-backed {@link CredentialsStore}, unless the application
         * already supplies its own.
         *
         * @param userRepository repository for {@code authz_user} rows
         * @return the JPA credentials store
         */
        @Bean
        @ConditionalOnMissingBean(CredentialsStore.class)
        JpaCredentialsStore javalibsJpaCredentialsStore(AuthzUserRepository userRepository) {
            return new JpaCredentialsStore(userRepository);
        }

        /**
         * Exposes the default JPA-backed {@link RefreshTokenStore}, unless the application
         * already supplies its own.
         *
         * @param repository repository for {@code authz_refresh_token} rows
         * @return the JPA refresh token store
         */
        @Bean
        @ConditionalOnMissingBean(RefreshTokenStore.class)
        JpaRefreshTokenStore javalibsJpaRefreshTokenStore(
                AuthzRefreshTokenRepository repository) {
            return new JpaRefreshTokenStore(repository);
        }
    }
}
