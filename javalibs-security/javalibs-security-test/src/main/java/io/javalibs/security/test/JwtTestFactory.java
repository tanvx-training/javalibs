package io.javalibs.security.test;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent factory minting HS256-signed JWTs for tests.
 *
 * <p>Defaults are aligned with the javalibs security core conventions (claim names
 * {@code roles}, {@code preferred_username}, {@code email}, {@code tenant}) so a token minted
 * here validates out of the box against a {@code JwtTokenValidator} — or a service under test —
 * configured with the same secret:</p>
 *
 * <pre>{@code
 * String bearer = JwtTestFactory.create()
 *         .subject("user-1")
 *         .username("jdoe")
 *         .roles("ADMIN")
 *         .bearer();
 *
 * mockMvc.perform(get("/api/admin").header("Authorization", bearer)) ...
 * }</pre>
 *
 * <p>To mint an <em>expired</em> token, move the issue time into the past:
 * {@code issuedAt(Instant.now().minus(Duration.ofHours(2))).expiresIn(Duration.ofHours(1))} —
 * the expiration is always computed as {@code issuedAt + expiresIn}.</p>
 *
 * <p>Instances are mutable builders and not thread-safe; create one per token.</p>
 */
public final class JwtTestFactory {

    /**
     * Default HMAC secret (&gt;= 32 bytes) shared by tokens minted here and validators under
     * test. Never use this value outside of tests.
     */
    public static final String DEFAULT_TEST_SECRET = "javalibs-test-secret-key-0123456789abcdef";

    private String secret = DEFAULT_TEST_SECRET;
    private String subject = "test-user";
    private String tokenId = java.util.UUID.randomUUID().toString();
    private String username;
    private String email;
    private String tenant;
    private String issuer;
    private String audience;
    private Instant issuedAt;
    private Duration expiresIn = Duration.ofHours(1);
    private final Set<String> roles = new LinkedHashSet<>();
    private final Map<String, Object> extraClaims = new LinkedHashMap<>();

    private JwtTestFactory() {
    }

    /**
     * Creates a factory pre-configured with the documented defaults
     * (subject {@code test-user}, no roles, expires in one hour, {@link #DEFAULT_TEST_SECRET}).
     *
     * @return a fresh factory
     */
    public static JwtTestFactory create() {
        return new JwtTestFactory();
    }

    /**
     * Sets the {@code sub} claim.
     *
     * @param subject the subject / user id
     * @return this factory
     */
    public JwtTestFactory subject(String subject) {
        this.subject = subject;
        return this;
    }

    /**
     * Sets the {@code preferred_username} claim.
     *
     * @param username the username
     * @return this factory
     */
    public JwtTestFactory username(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets the {@code email} claim.
     *
     * @param email the e-mail address
     * @return this factory
     */
    public JwtTestFactory email(String email) {
        this.email = email;
        return this;
    }

    /**
     * Adds roles to the {@code roles} claim.
     *
     * @param roles the roles to add
     * @return this factory
     */
    public JwtTestFactory roles(String... roles) {
        if (roles != null) {
            this.roles.addAll(List.of(roles));
        }
        return this;
    }

    /**
     * Sets the {@code tenant} claim.
     *
     * @param tenant the tenant id
     * @return this factory
     */
    public JwtTestFactory tenant(String tenant) {
        this.tenant = tenant;
        return this;
    }

    /**
     * Sets the {@code iss} claim.
     *
     * @param issuer the issuer
     * @return this factory
     */
    public JwtTestFactory issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    /**
     * Sets the {@code aud} claim.
     *
     * @param audience the audience
     * @return this factory
     */
    public JwtTestFactory audience(String audience) {
        this.audience = audience;
        return this;
    }

    /**
     * Adds an arbitrary custom claim.
     *
     * @param key   the claim name
     * @param value the claim value
     * @return this factory
     */
    public JwtTestFactory claim(String key, Object value) {
        this.extraClaims.put(key, value);
        return this;
    }

    /**
     * Sets the token lifetime, computed from the issue time
     * ({@code exp = issuedAt + expiresIn}). Defaults to one hour.
     *
     * @param expiresIn the lifetime
     * @return this factory
     */
    public JwtTestFactory expiresIn(Duration expiresIn) {
        this.expiresIn = expiresIn;
        return this;
    }

    /**
     * Sets the {@code iat} claim. Defaults to "now" at build time.
     *
     * @param issuedAt the issue instant
     * @return this factory
     */
    public JwtTestFactory issuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
        return this;
    }

    /**
     * Overrides the signing secret (must be at least 32 bytes).
     *
     * @param secret the HMAC secret
     * @return this factory
     */
    public JwtTestFactory secret(String secret) {
        this.secret = secret;
        return this;
    }

    /**
     * Overrides the token id ({@code jti} claim). A random UUID is used by default so
     * minted tokens are individually revocable through a {@code TokenBlacklist};
     * pass {@code null} to mint a token without a {@code jti}.
     *
     * @param tokenId the token id, or {@code null} to omit the claim
     * @return this factory
     */
    public JwtTestFactory tokenId(String tokenId) {
        this.tokenId = tokenId;
        return this;
    }

    /**
     * Mints the signed compact JWT.
     *
     * @return the compact token string
     */
    public String token() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant iat = (issuedAt != null) ? issuedAt : Instant.now();

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(iat))
                .expiration(Date.from(iat.plus(expiresIn)));
        if (tokenId != null) {
            builder.id(tokenId);
        }
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null) {
            builder.audience().add(audience).and();
        }
        if (!roles.isEmpty()) {
            builder.claim("roles", List.copyOf(roles));
        }
        if (username != null) {
            builder.claim("preferred_username", username);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        if (tenant != null) {
            builder.claim("tenant", tenant);
        }
        extraClaims.forEach(builder::claim);

        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    /**
     * Mints the token and prefixes it for direct use as an {@code Authorization} header value.
     *
     * @return {@code "Bearer " + token()}
     */
    public String bearer() {
        return "Bearer " + token();
    }
}
