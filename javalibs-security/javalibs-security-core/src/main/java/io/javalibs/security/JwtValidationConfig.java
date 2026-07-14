package io.javalibs.security;

import java.time.Duration;

/**
 * Immutable configuration for {@link JwtTokenValidator}.
 *
 * <p>At least one of {@code hmacSecret} (for HS256 and friends) or {@code rsaPublicKeyPem}
 * (for RS256 and friends) must be provided. Optional issuer/audience values are enforced
 * during validation when set. Claim names default to common OpenID Connect conventions and
 * may be overridden for other identity providers.</p>
 *
 * @param hmacSecret      shared secret for HMAC-signed tokens; must be at least 32 bytes when
 *                        used (may be {@code null} if an RSA public key is configured)
 * @param rsaPublicKeyPem RSA public key in PEM (X.509 SubjectPublicKeyInfo) format for
 *                        RSA-signed tokens (may be {@code null} if an HMAC secret is configured)
 * @param issuer          expected {@code iss} claim; not checked when {@code null}
 * @param audience        expected {@code aud} claim; not checked when {@code null}
 * @param clockSkew       tolerated clock skew when checking temporal claims, defaults to 30s
 * @param rolesClaim      name of the claim holding the roles, defaults to {@code roles}
 * @param usernameClaim   name of the claim holding the username, defaults to
 *                        {@code preferred_username}
 * @param emailClaim      name of the claim holding the e-mail address, defaults to {@code email}
 * @param tenantClaim     name of the claim holding the tenant id, defaults to {@code tenant}
 */
public record JwtValidationConfig(
        String hmacSecret,
        String rsaPublicKeyPem,
        String issuer,
        String audience,
        Duration clockSkew,
        String rolesClaim,
        String usernameClaim,
        String emailClaim,
        String tenantClaim) {

    /** Default clock skew applied to temporal claim checks. */
    public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(30);

    /** Default name of the roles claim. */
    public static final String DEFAULT_ROLES_CLAIM = "roles";

    /** Default name of the username claim (OpenID Connect convention). */
    public static final String DEFAULT_USERNAME_CLAIM = "preferred_username";

    /** Default name of the e-mail claim. */
    public static final String DEFAULT_EMAIL_CLAIM = "email";

    /** Default name of the tenant claim. */
    public static final String DEFAULT_TENANT_CLAIM = "tenant";

    /**
     * Canonical constructor validating the key material and applying defaults for
     * unset optional values.
     */
    public JwtValidationConfig {
        if (isBlank(hmacSecret) && isBlank(rsaPublicKeyPem)) {
            throw new IllegalArgumentException(
                    "JwtValidationConfig requires at least one of hmacSecret or rsaPublicKeyPem. "
                            + "Configure the shared HMAC secret used to sign tokens, or the PEM encoded "
                            + "RSA public key matching the private signing key.");
        }
        clockSkew = (clockSkew == null) ? DEFAULT_CLOCK_SKEW : clockSkew;
        rolesClaim = isBlank(rolesClaim) ? DEFAULT_ROLES_CLAIM : rolesClaim;
        usernameClaim = isBlank(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
        emailClaim = isBlank(emailClaim) ? DEFAULT_EMAIL_CLAIM : emailClaim;
        tenantClaim = isBlank(tenantClaim) ? DEFAULT_TENANT_CLAIM : tenantClaim;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Creates a new {@link Builder} pre-populated with the documented defaults.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link JwtValidationConfig}. Not thread-safe.
     */
    public static final class Builder {

        private String hmacSecret;
        private String rsaPublicKeyPem;
        private String issuer;
        private String audience;
        private Duration clockSkew = DEFAULT_CLOCK_SKEW;
        private String rolesClaim = DEFAULT_ROLES_CLAIM;
        private String usernameClaim = DEFAULT_USERNAME_CLAIM;
        private String emailClaim = DEFAULT_EMAIL_CLAIM;
        private String tenantClaim = DEFAULT_TENANT_CLAIM;

        private Builder() {
        }

        /**
         * Sets the shared HMAC secret (at least 32 bytes).
         *
         * @param hmacSecret the shared secret
         * @return this builder
         */
        public Builder hmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
            return this;
        }

        /**
         * Sets the RSA public key in PEM format.
         *
         * @param rsaPublicKeyPem the PEM encoded public key
         * @return this builder
         */
        public Builder rsaPublicKeyPem(String rsaPublicKeyPem) {
            this.rsaPublicKeyPem = rsaPublicKeyPem;
            return this;
        }

        /**
         * Sets the expected issuer ({@code iss} claim).
         *
         * @param issuer the expected issuer
         * @return this builder
         */
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Sets the expected audience ({@code aud} claim).
         *
         * @param audience the expected audience
         * @return this builder
         */
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the tolerated clock skew for temporal claim checks.
         *
         * @param clockSkew the clock skew, {@code null} restores the default (30 seconds)
         * @return this builder
         */
        public Builder clockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
            return this;
        }

        /**
         * Sets the name of the roles claim.
         *
         * @param rolesClaim the claim name
         * @return this builder
         */
        public Builder rolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
            return this;
        }

        /**
         * Sets the name of the username claim.
         *
         * @param usernameClaim the claim name
         * @return this builder
         */
        public Builder usernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
            return this;
        }

        /**
         * Sets the name of the e-mail claim.
         *
         * @param emailClaim the claim name
         * @return this builder
         */
        public Builder emailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
            return this;
        }

        /**
         * Sets the name of the tenant claim.
         *
         * @param tenantClaim the claim name
         * @return this builder
         */
        public Builder tenantClaim(String tenantClaim) {
            this.tenantClaim = tenantClaim;
            return this;
        }

        /**
         * Builds the immutable configuration.
         *
         * @return the configuration
         * @throws IllegalArgumentException if neither an HMAC secret nor an RSA public key is set
         */
        public JwtValidationConfig build() {
            return new JwtValidationConfig(hmacSecret, rsaPublicKeyPem, issuer, audience,
                    clockSkew, rolesClaim, usernameClaim, emailClaim, tenantClaim);
        }
    }
}
