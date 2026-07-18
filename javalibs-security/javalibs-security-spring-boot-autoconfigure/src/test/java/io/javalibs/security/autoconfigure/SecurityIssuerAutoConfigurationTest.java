package io.javalibs.security.autoconfigure;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.JwtIssuerConfig;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.security.issuer.RefreshTokenStore;
import io.javalibs.security.issuer.StoredCredentials;
import io.javalibs.security.issuer.TokenIssuer;
import io.javalibs.security.issuer.web.AuthEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIssuerAutoConfigurationTest {

    private static final String SECRET_PROPERTY =
            "javalibs.security.jwt.secret=0123456789abcdef0123456789abcdef";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityIssuerAutoConfiguration.class))
            .withPropertyValues(SECRET_PROPERTY);

    @Configuration(proxyBeanMethods = false)
    static class StoreConfiguration {
        @Bean
        CredentialsStore credentialsStore() {
            return new CredentialsStore() {
                @Override public Optional<StoredCredentials> findByUsername(String username) {
                    return Optional.empty();
                }
                @Override public Optional<StoredCredentials> findByUserId(String userId) {
                    return Optional.empty();
                }
            };
        }
        @Bean
        RefreshTokenStore refreshTokenStore() {
            return new RefreshTokenStore() {
                @Override public void save(RefreshTokenRecord record) { }
                @Override public Optional<RefreshTokenRecord> findByTokenHash(String hash) {
                    return Optional.empty();
                }
                @Override public boolean markRotated(String h, String to, Instant at) {
                    return true;
                }
                @Override public void revoke(String h, Instant at) { }
                @Override public void revokeFamily(String familyId, Instant at) { }
            };
        }
    }

    @Test
    void wiresIssuerBeansWhenStoresPresent() {
        runner.withUserConfiguration(StoreConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PasswordHasher.class);
            assertThat(context).hasSingleBean(TokenIssuer.class);
            assertThat(context).hasSingleBean(AuthenticationService.class);
            assertThat(context).hasSingleBean(AuthEndpoints.class);
        });
    }

    @Test
    void skipsAuthenticationServiceWithoutStores() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TokenIssuer.class);
            assertThat(context).doesNotHaveBean(AuthenticationService.class);
            assertThat(context).doesNotHaveBean(AuthEndpoints.class);
        });
    }

    @Test
    void endpointsCanBeDisabled() {
        runner.withUserConfiguration(StoreConfiguration.class)
                .withPropertyValues("javalibs.security.issuer.endpoints.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationService.class);
                    assertThat(context).doesNotHaveBean(AuthEndpoints.class);
                });
    }

    @Test
    void backsOffWhenDisabled() {
        runner.withPropertyValues("javalibs.security.issuer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TokenIssuer.class));
    }

    @Test
    void jwtIssuerConfigUsesDefaultRolesClaim() {
        runner.run(context -> {
            JwtIssuerConfig config = context.getBean(JwtIssuerConfig.class);
            assertThat(config.rolesClaim()).isEqualTo("roles");
        });
    }

    @Test
    void jwtIssuerConfigHonoursCustomRolesClaim() {
        runner.withPropertyValues("javalibs.security.jwt.roles-claim=authorities")
                .run(context -> {
                    JwtIssuerConfig config = context.getBean(JwtIssuerConfig.class);
                    assertThat(config.rolesClaim()).isEqualTo("authorities");
                });
    }
}
