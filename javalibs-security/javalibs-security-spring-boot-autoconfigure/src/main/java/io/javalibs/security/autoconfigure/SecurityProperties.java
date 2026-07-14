package io.javalibs.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the javalibs security module, bound from the
 * {@code javalibs.security.*} namespace.
 *
 * <pre>{@code
 * javalibs:
 *   security:
 *     enabled: true
 *     permit-all:
 *       - /actuator/health
 *       - /public/**
 *     jwt:
 *       secret: change-me-to-a-random-32+-byte-secret
 *       issuer: https://sso.example.com
 *       audience: orders-api
 *       clock-skew: 30s
 * }</pre>
 */
@ConfigurationProperties("javalibs.security")
public class SecurityProperties {

    /**
     * Whether the javalibs security auto-configuration is active.
     */
    private boolean enabled = true;

    /**
     * Authentication mode: {@code jwt} (the library validates tokens itself using
     * {@code javalibs.security.jwt.*}) or {@code oauth2-resource-server} (token
     * validation is delegated to Spring Security's OAuth2 Resource Server, driven
     * by {@code spring.security.oauth2.resourceserver.jwt.*} — use with Keycloak
     * or any OIDC provider). Both modes expose the same UserContext API.
     */
    private String mode = "jwt";

    /**
     * Ant-style path patterns that are accessible without authentication.
     */
    private List<String> permitAll = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/error",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"));

    /**
     * JWT validation settings.
     */
    private final Jwt jwt = new Jwt();

    /**
     * Token revocation (blacklist) settings.
     */
    private final Blacklist blacklist = new Blacklist();

    /**
     * Returns whether the auto-configuration is enabled.
     *
     * @return {@code true} when enabled (the default)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the auto-configuration.
     *
     * @param enabled the new value
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the path patterns accessible without authentication.
     *
     * @return the permit-all patterns
     */
    public List<String> getPermitAll() {
        return permitAll;
    }

    /**
     * Replaces the path patterns accessible without authentication.
     *
     * @param permitAll the new patterns
     */
    public void setPermitAll(List<String> permitAll) {
        this.permitAll = permitAll;
    }

    /**
     * Returns the JWT validation settings.
     *
     * @return the nested JWT properties
     */
    public Jwt getJwt() {
        return jwt;
    }

    /**
     * Returns the authentication mode.
     *
     * @return {@code "jwt"} (default) or {@code "oauth2-resource-server"}
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the authentication mode.
     *
     * @param mode {@code "jwt"} or {@code "oauth2-resource-server"}
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Returns the token revocation settings.
     *
     * @return the nested blacklist properties
     */
    public Blacklist getBlacklist() {
        return blacklist;
    }

    /**
     * Token revocation settings ({@code javalibs.security.blacklist.*}).
     */
    public static class Blacklist {

        /**
         * Blacklist backend: {@code none} (default — tokens cannot be revoked),
         * {@code in-memory} (single-instance deployments only) or {@code redis}
         * (shared across instances; requires spring-data-redis and a
         * StringRedisTemplate bean).
         */
        private String mode = "none";

        /**
         * Redis key prefix for revocation entries (redis mode only).
         */
        private String keyPrefix = "javalibs:security:revoked:";

        /**
         * Returns the blacklist backend mode.
         *
         * @return {@code "none"}, {@code "in-memory"} or {@code "redis"}
         */
        public String getMode() {
            return mode;
        }

        /**
         * Sets the blacklist backend mode.
         *
         * @param mode {@code "none"}, {@code "in-memory"} or {@code "redis"}
         */
        public void setMode(String mode) {
            this.mode = mode;
        }

        /**
         * Returns the Redis key prefix.
         *
         * @return the key prefix
         */
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * Sets the Redis key prefix.
         *
         * @param keyPrefix the key prefix
         */
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /**
     * JWT validation settings ({@code javalibs.security.jwt.*}).
     */
    public static class Jwt {

        /**
         * Shared HMAC secret used to verify HS256-signed tokens. Must be at least 32 bytes.
         * Either this or {@code public-key} must be configured.
         */
        private String secret;

        /**
         * RSA public key in PEM (X.509 SubjectPublicKeyInfo) format used to verify
         * RS256-signed tokens. Either this or {@code secret} must be configured.
         */
        private String publicKey;

        /**
         * Expected {@code iss} claim. Not verified when unset.
         */
        private String issuer;

        /**
         * Expected {@code aud} claim. Not verified when unset.
         */
        private String audience;

        /**
         * Tolerated clock skew when checking temporal claims (exp, nbf, iat).
         */
        private Duration clockSkew = Duration.ofSeconds(30);

        /**
         * Name of the claim holding the user's roles.
         */
        private String rolesClaim = "roles";

        /**
         * Name of the claim holding the username.
         */
        private String usernameClaim = "preferred_username";

        /**
         * Name of the claim holding the e-mail address.
         */
        private String emailClaim = "email";

        /**
         * Name of the claim holding the tenant identifier.
         */
        private String tenantClaim = "tenant";

        /**
         * Returns the shared HMAC secret.
         *
         * @return the secret, or {@code null} when unset
         */
        public String getSecret() {
            return secret;
        }

        /**
         * Sets the shared HMAC secret.
         *
         * @param secret the secret
         */
        public void setSecret(String secret) {
            this.secret = secret;
        }

        /**
         * Returns the PEM encoded RSA public key.
         *
         * @return the public key, or {@code null} when unset
         */
        public String getPublicKey() {
            return publicKey;
        }

        /**
         * Sets the PEM encoded RSA public key.
         *
         * @param publicKey the public key
         */
        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * Returns the expected issuer.
         *
         * @return the issuer, or {@code null} when not enforced
         */
        public String getIssuer() {
            return issuer;
        }

        /**
         * Sets the expected issuer.
         *
         * @param issuer the issuer
         */
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        /**
         * Returns the expected audience.
         *
         * @return the audience, or {@code null} when not enforced
         */
        public String getAudience() {
            return audience;
        }

        /**
         * Sets the expected audience.
         *
         * @param audience the audience
         */
        public void setAudience(String audience) {
            this.audience = audience;
        }

        /**
         * Returns the tolerated clock skew.
         *
         * @return the clock skew
         */
        public Duration getClockSkew() {
            return clockSkew;
        }

        /**
         * Sets the tolerated clock skew.
         *
         * @param clockSkew the clock skew
         */
        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        /**
         * Returns the roles claim name.
         *
         * @return the claim name
         */
        public String getRolesClaim() {
            return rolesClaim;
        }

        /**
         * Sets the roles claim name.
         *
         * @param rolesClaim the claim name
         */
        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        /**
         * Returns the username claim name.
         *
         * @return the claim name
         */
        public String getUsernameClaim() {
            return usernameClaim;
        }

        /**
         * Sets the username claim name.
         *
         * @param usernameClaim the claim name
         */
        public void setUsernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
        }

        /**
         * Returns the e-mail claim name.
         *
         * @return the claim name
         */
        public String getEmailClaim() {
            return emailClaim;
        }

        /**
         * Sets the e-mail claim name.
         *
         * @param emailClaim the claim name
         */
        public void setEmailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
        }

        /**
         * Returns the tenant claim name.
         *
         * @return the claim name
         */
        public String getTenantClaim() {
            return tenantClaim;
        }

        /**
         * Sets the tenant claim name.
         *
         * @param tenantClaim the claim name
         */
        public void setTenantClaim(String tenantClaim) {
            this.tenantClaim = tenantClaim;
        }
    }
}
