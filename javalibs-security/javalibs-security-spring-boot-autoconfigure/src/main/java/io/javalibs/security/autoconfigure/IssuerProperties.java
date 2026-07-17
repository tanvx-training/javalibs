package io.javalibs.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for token issuing, bound from the
 * {@code javalibs.security.issuer.*} namespace.
 *
 * <pre>{@code
 * javalibs:
 *   security:
 *     issuer:
 *       enabled: true
 *       private-key: |
 *         -----BEGIN PRIVATE KEY-----
 *         ...
 *         -----END PRIVATE KEY-----
 *       access-token-ttl: 15m
 *       refresh-token-ttl: 30d
 *       endpoints:
 *         enabled: true
 *         base-path: /auth
 * }</pre>
 */
@ConfigurationProperties("javalibs.security.issuer")
public class IssuerProperties {

    /**
     * Whether the issuer auto-configuration is active.
     */
    private boolean enabled = true;

    /**
     * RSA private key (PKCS#8 PEM) used to RS256-sign issued tokens. When unset, tokens are
     * HS256-signed with {@code javalibs.security.jwt.secret}.
     */
    private String privateKey;

    /**
     * Lifetime of issued access tokens.
     */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /**
     * Lifetime of issued refresh tokens.
     */
    private Duration refreshTokenTtl = Duration.ofDays(30);

    /**
     * Built-in REST endpoint settings.
     */
    private final Endpoints endpoints = new Endpoints();

    /**
     * Returns whether the issuer auto-configuration is enabled.
     *
     * @return {@code true} when enabled (the default)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the issuer auto-configuration.
     *
     * @param enabled the new value
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the PEM encoded RSA private key.
     *
     * @return the private key, or {@code null} when unset
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * Sets the PEM encoded RSA private key.
     *
     * @param privateKey the private key
     */
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Returns the access token time-to-live.
     *
     * @return the access token TTL
     */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Sets the access token time-to-live.
     *
     * @param accessTokenTtl the access token TTL
     */
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * Returns the refresh token time-to-live.
     *
     * @return the refresh token TTL
     */
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /**
     * Sets the refresh token time-to-live.
     *
     * @param refreshTokenTtl the refresh token TTL
     */
    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /**
     * Returns the built-in REST endpoint settings.
     *
     * @return the nested endpoints properties
     */
    public Endpoints getEndpoints() {
        return endpoints;
    }

    /**
     * Built-in REST endpoint settings ({@code javalibs.security.issuer.endpoints.*}).
     */
    public static class Endpoints {

        /**
         * Whether the built-in {@code /auth} endpoints are registered.
         */
        private boolean enabled = true;

        /**
         * Base path of the built-in endpoints. Add it to {@code javalibs.security.permit-all}.
         */
        private String basePath = "/auth";

        /**
         * Returns whether the built-in endpoints are registered.
         *
         * @return {@code true} when enabled (the default)
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the built-in endpoints.
         *
         * @param enabled the new value
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the base path of the built-in endpoints.
         *
         * @return the base path
         */
        public String getBasePath() {
            return basePath;
        }

        /**
         * Sets the base path of the built-in endpoints.
         *
         * @param basePath the base path
         */
        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }
}
