package io.javalibs.security.autoconfigure;

import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.JwtIssuerConfig;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.security.issuer.RefreshTokenStore;
import io.javalibs.security.issuer.TokenIssuer;
import io.javalibs.security.issuer.web.AuthEndpoints;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.time.Clock;

/**
 * Auto-configuration for token issuing: signs and rotates JWT access/refresh tokens for
 * username/password authentication.
 *
 * <p>Requires application-provided (or javalibs-authz-jpa-provided) {@link CredentialsStore}
 * and {@link RefreshTokenStore} beans for the {@link AuthenticationService} to be wired;
 * without them, only the stateless {@link PasswordHasher}, {@link JwtIssuerConfig} and
 * {@link TokenIssuer} beans are created. Signing reuses the {@code javalibs.security.jwt.*}
 * settings (issuer, audience, HMAC secret) so every javalibs service can validate tokens
 * issued here out of the box.</p>
 *
 * <p>Declared to run after {@code io.javalibs.authz.jpa.autoconfigure.AuthzJpaAutoConfiguration}
 * ({@code afterName}, string reference — no compile-time dependency on javalibs-authz-jpa):
 * when that module is on the classpath, it is the default source of the {@link CredentialsStore}
 * and {@link RefreshTokenStore} beans this configuration consumes.</p>
 */
// The default CredentialsStore/RefreshTokenStore beans come from AuthzJpaAutoConfiguration
// (javalibs-authz-jpa) when it's present; today's alphabetical ordering is accidental.
@AutoConfiguration(afterName = "io.javalibs.authz.jpa.autoconfigure.AuthzJpaAutoConfiguration")
@ConditionalOnClass(AuthenticationService.class)
@ConditionalOnProperty(prefix = "javalibs.security.issuer", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SecurityProperties.class, IssuerProperties.class})
public class SecurityIssuerAutoConfiguration {

    /**
     * Creates the default {@link PasswordHasher} (delegating BCrypt/Argon2/SCrypt encoder).
     *
     * @return the password hasher bean
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher javalibsPasswordHasher() {
        return new PasswordHasher();
    }

    /**
     * Builds the {@link JwtIssuerConfig} from {@code javalibs.security.jwt.*} (issuer,
     * audience, HMAC secret) and {@code javalibs.security.issuer.*} (RSA private key, token
     * TTLs), so issued tokens validate against the same settings as
     * {@code SecurityWebMvcAutoConfiguration}.
     *
     * @param security the shared JWT validation/signing settings
     * @param issuer the issuer-specific settings
     * @return the issuer configuration bean
     * @throws IllegalStateException when neither an HMAC secret nor an RSA private key is
     *     configured
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtIssuerConfig javalibsJwtIssuerConfig(SecurityProperties security,
            IssuerProperties issuer) {
        SecurityProperties.Jwt jwt = security.getJwt();
        boolean hasPrivateKey = StringUtils.hasText(issuer.getPrivateKey());
        if (!hasPrivateKey && !StringUtils.hasText(jwt.getSecret())) {
            throw new IllegalStateException(
                    "Token issuing requires javalibs.security.jwt.secret (HS256) or "
                            + "javalibs.security.issuer.private-key (RS256). Configure one, "
                            + "or disable issuing with javalibs.security.issuer.enabled=false.");
        }
        return JwtIssuerConfig.builder()
                .hmacSecret(hasPrivateKey ? null : jwt.getSecret())
                .rsaPrivateKeyPem(issuer.getPrivateKey())
                .issuer(jwt.getIssuer())
                .audience(jwt.getAudience())
                .accessTokenTtl(issuer.getAccessTokenTtl())
                .refreshTokenTtl(issuer.getRefreshTokenTtl())
                .usernameClaim(jwt.getUsernameClaim())
                .emailClaim(jwt.getEmailClaim())
                .build();
    }

    /**
     * Creates the {@link TokenIssuer} that signs access tokens using the system UTC clock.
     *
     * @param config the issuer configuration
     * @return the token issuer bean
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenIssuer javalibsTokenIssuer(JwtIssuerConfig config) {
        return new TokenIssuer(config, Clock.systemUTC());
    }

    /**
     * Wires the {@link AuthenticationService} once both a {@link CredentialsStore} and a
     * {@link RefreshTokenStore} bean are present. The optional {@link TokenBlacklist} is
     * resolved lazily via {@link ObjectProvider} so logout can also revoke the access token
     * when a blacklist backend is configured.
     *
     * @param credentials the credentials store SPI implementation
     * @param refreshTokens the refresh token store SPI implementation
     * @param passwordHasher verifies raw passwords against stored hashes
     * @param tokenIssuer signs access tokens
     * @param config the issuer configuration, used for access/refresh token TTLs
     * @param tokenBlacklist optional access-token blacklist
     * @return the authentication service bean
     */
    @Bean
    @ConditionalOnBean({CredentialsStore.class, RefreshTokenStore.class})
    @ConditionalOnMissingBean
    public AuthenticationService javalibsAuthenticationService(CredentialsStore credentials,
            RefreshTokenStore refreshTokens, PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer, JwtIssuerConfig config,
            ObjectProvider<TokenBlacklist> tokenBlacklist) {
        return new AuthenticationService(credentials, refreshTokens, passwordHasher,
                tokenIssuer, config, Clock.systemUTC(), tokenBlacklist.getIfAvailable());
    }

    /**
     * Registers the built-in {@code /auth} REST endpoints (login, refresh, logout) once an
     * {@link AuthenticationService} bean exists, the application is a servlet web
     * application, and {@code javalibs.security.issuer.endpoints.enabled} is not set to
     * {@code false}.
     *
     * @param authenticationService the authentication service backing the endpoints
     * @return the auth endpoints bean
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthenticationService.class)
    @ConditionalOnProperty(prefix = "javalibs.security.issuer.endpoints", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AuthEndpoints javalibsAuthEndpoints(AuthenticationService authenticationService) {
        return new AuthEndpoints(authenticationService);
    }
}
