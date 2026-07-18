package io.javalibs.security.issuer;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Signs JWT access tokens (HS256 via the shared secret, or RS256 via a PKCS#8 private key)
 * carrying only identity claims: sub, jti, iat, exp, optional iss/aud, username and email.
 * Permissions are deliberately NOT embedded — they are evaluated server-side by javalibs-authz.
 */
public final class TokenIssuer {

    private static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final Set<String> RESERVED_CLAIMS =
            Set.of("sub", "iss", "aud", "exp", "nbf", "iat", "jti");

    private final JwtIssuerConfig config;
    private final Clock clock;
    private final Key signingKey;

    /**
     * Creates a new token issuer with the given configuration and clock.
     *
     * @param config the issuer configuration (must not be null)
     * @param clock the clock to use for token timestamps (must not be null)
     */
    public TokenIssuer(JwtIssuerConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.signingKey = buildSigningKey(config);
    }

    /**
     * Issues a signed access token for the given identity.
     *
     * @param userId the user ID to embed in the 'sub' claim (must not be null)
     * @param username the username to embed in the configured username claim (may be null)
     * @param email the email to embed in the configured email claim (may be null)
     * @return a freshly signed access token with its id and expiry
     */
    public IssuedToken issue(String userId, String username, String email) {
        return issue(userId, username, email, Set.of(), Map.of());
    }

    /**
     * Issues a signed access token carrying roles and optional custom claims.
     *
     * @param userId the stable user id, written to the {@code sub} claim (must not be null)
     * @param username the username, written under the configured username claim (skipped when null)
     * @param email the email, written under the configured email claim (skipped when null)
     * @param roles the roles, written as a JSON array under the configured roles claim (skipped
     *     when null or empty)
     * @param extraClaims additional claims to include; reserved names ({@code sub}, {@code iss},
     *     {@code aud}, {@code exp}, {@code nbf}, {@code iat}, {@code jti}) and the mapped
     *     username/email/roles claim names are ignored so they cannot be spoofed
     * @return the signed token with its id and expiry
     */
    public IssuedToken issue(String userId, String username, String email,
            Set<String> roles, Map<String, Object> extraClaims) {
        Objects.requireNonNull(userId, "userId must not be null");
        String tokenId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.accessTokenTtl());

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (config.issuer() != null && !config.issuer().isBlank()) {
            builder.issuer(config.issuer());
        }
        if (config.audience() != null && !config.audience().isBlank()) {
            builder.audience().add(config.audience()).and();
        }
        if (username != null) {
            builder.claim(config.usernameClaim(), username);
        }
        if (email != null) {
            builder.claim(config.emailClaim(), email);
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim(config.rolesClaim(), List.copyOf(roles));
        }
        if (extraClaims != null) {
            for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                String name = entry.getKey();
                if (name == null || RESERVED_CLAIMS.contains(name)
                        || name.equals(config.usernameClaim())
                        || name.equals(config.emailClaim())
                        || name.equals(config.rolesClaim())) {
                    continue;
                }
                builder.claim(name, entry.getValue());
            }
        }
        if (signingKey instanceof SecretKey secretKey) {
            builder.signWith(secretKey, Jwts.SIG.HS256);
        } else {
            builder.signWith((java.security.PrivateKey) signingKey, Jwts.SIG.RS256);
        }
        return new IssuedToken(builder.compact(), tokenId, expiresAt);
    }

    private static Key buildSigningKey(JwtIssuerConfig config) {
        if (config.hmacSecret() != null && !config.hmacSecret().isBlank()) {
            return Keys.hmacShaKeyFor(config.hmacSecret().getBytes(StandardCharsets.UTF_8));
        }
        String base64 = config.rsaPrivateKeyPem()
                .replace(PEM_PRIVATE_KEY_BEGIN, "")
                .replace(PEM_PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unable to parse the configured RSA private key (PKCS#8 PEM expected): "
                            + ex.getMessage(), ex);
        }
    }

    /**
     * A freshly signed access token with its id and expiry.
     *
     * @param token the signed JWT string
     * @param tokenId the unique token ID (jti claim)
     * @param expiresAt the expiration timestamp
     */
    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }
}
