package io.javalibs.security.issuer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Immutable configuration for {@link TokenIssuer}. Mirror of JwtValidationConfig on the
 * issuing side: configure the same secret (HS256) or the private key matching the
 * validator's public key (RS256) so every javalibs service can validate issued tokens.
 */
public record JwtIssuerConfig(
        String hmacSecret,
        String rsaPrivateKeyPem,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String usernameClaim,
        String emailClaim) {

    public static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(30);
    public static final String DEFAULT_USERNAME_CLAIM = "preferred_username";
    public static final String DEFAULT_EMAIL_CLAIM = "email";
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    public JwtIssuerConfig {
        if (isBlank(hmacSecret) && isBlank(rsaPrivateKeyPem)) {
            throw new IllegalArgumentException(
                    "JwtIssuerConfig requires at least one of hmacSecret or rsaPrivateKeyPem");
        }
        if (!isBlank(hmacSecret)
                && hmacSecret.getBytes(StandardCharsets.UTF_8).length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "The configured HMAC secret is shorter than 32 bytes (256 bits)");
        }
        accessTokenTtl = (accessTokenTtl == null) ? DEFAULT_ACCESS_TOKEN_TTL : accessTokenTtl;
        refreshTokenTtl = (refreshTokenTtl == null) ? DEFAULT_REFRESH_TOKEN_TTL : refreshTokenTtl;
        usernameClaim = isBlank(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
        emailClaim = isBlank(emailClaim) ? DEFAULT_EMAIL_CLAIM : emailClaim;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link JwtIssuerConfig}. Fluent API to configure issuer identity
     * and token lifetimes.
     */
    public static final class Builder {
        private String hmacSecret;
        private String rsaPrivateKeyPem;
        private String issuer;
        private String audience;
        private Duration accessTokenTtl = DEFAULT_ACCESS_TOKEN_TTL;
        private Duration refreshTokenTtl = DEFAULT_REFRESH_TOKEN_TTL;
        private String usernameClaim = DEFAULT_USERNAME_CLAIM;
        private String emailClaim = DEFAULT_EMAIL_CLAIM;

        /**
         * Sets the HMAC secret for HS256 signing.
         *
         * @param hmacSecret the shared secret (at least 32 bytes / 256 bits)
         * @return this builder
         */
        public Builder hmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
            return this;
        }

        /**
         * Sets the RSA private key (PKCS#8 PEM) for RS256 signing.
         *
         * @param rsaPrivateKeyPem the RSA private key in PEM format
         * @return this builder
         */
        public Builder rsaPrivateKeyPem(String rsaPrivateKeyPem) {
            this.rsaPrivateKeyPem = rsaPrivateKeyPem;
            return this;
        }

        /**
         * Sets the issuer claim (iss).
         *
         * @param issuer the issuer identifier
         * @return this builder
         */
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Sets the audience claim (aud).
         *
         * @param audience the audience identifier
         * @return this builder
         */
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the access token time-to-live.
         *
         * @param accessTokenTtl the TTL for access tokens (defaults to 15 minutes)
         * @return this builder
         */
        public Builder accessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
            return this;
        }

        /**
         * Sets the refresh token time-to-live.
         *
         * @param refreshTokenTtl the TTL for refresh tokens (defaults to 30 days)
         * @return this builder
         */
        public Builder refreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
            return this;
        }

        /**
         * Sets the claim name for the username (defaults to "preferred_username").
         *
         * @param usernameClaim the custom username claim name
         * @return this builder
         */
        public Builder usernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
            return this;
        }

        /**
         * Sets the claim name for the email (defaults to "email").
         *
         * @param emailClaim the custom email claim name
         * @return this builder
         */
        public Builder emailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
            return this;
        }

        /**
         * Builds the immutable {@link JwtIssuerConfig}.
         *
         * @return a new JwtIssuerConfig instance
         */
        public JwtIssuerConfig build() {
            return new JwtIssuerConfig(hmacSecret, rsaPrivateKeyPem, issuer, audience,
                    accessTokenTtl, refreshTokenTtl, usernameClaim, emailClaim);
        }
    }
}
