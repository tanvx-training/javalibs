package io.javalibs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link TokenValidator} implementation backed by <a href="https://github.com/jwtk/jjwt">JJWT</a>.
 *
 * <p>Supports HMAC (shared secret) and RSA (PEM public key) signed tokens. Issuer and audience
 * are enforced when configured, and the configured clock skew is applied to all temporal claim
 * checks. Verified claims are mapped to a {@link UserContext}:</p>
 *
 * <ul>
 *   <li>{@code sub} &rarr; {@link UserContext#userId()}</li>
 *   <li>the configured username/e-mail/tenant claims &rarr; the corresponding fields</li>
 *   <li>the configured roles claim &rarr; {@link UserContext#roles()}; the claim value may be a
 *       JSON array or a single comma/whitespace separated string</li>
 *   <li>every remaining non-registered claim &rarr; {@link UserContext#attributes()}</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public final class JwtTokenValidator implements TokenValidator {

    private static final int MIN_HMAC_SECRET_BYTES = 32;

    private static final String PEM_PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private static final Set<String> REGISTERED_CLAIMS = Set.of(
            Claims.SUBJECT, Claims.ISSUER, Claims.AUDIENCE, Claims.EXPIRATION,
            Claims.NOT_BEFORE, Claims.ISSUED_AT, Claims.ID);

    private final JwtValidationConfig config;
    private final JwtParser parser;

    /**
     * Creates a validator from the given configuration.
     *
     * @param config the validation configuration
     * @throws IllegalArgumentException if the HMAC secret is shorter than 32 bytes or the RSA
     *                                  public key PEM cannot be parsed
     */
    public JwtTokenValidator(JwtValidationConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");

        JwtParserBuilder builder = Jwts.parser();
        if (hasText(config.hmacSecret())) {
            byte[] secretBytes = config.hmacSecret().getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < MIN_HMAC_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "The configured HMAC secret is too short: it is " + secretBytes.length
                                + " bytes but HS256 requires at least " + MIN_HMAC_SECRET_BYTES
                                + " bytes (256 bits). Configure a longer, randomly generated secret.");
            }
            builder.verifyWith(Keys.hmacShaKeyFor(secretBytes));
        } else {
            builder.verifyWith(parseRsaPublicKey(config.rsaPublicKeyPem()));
        }

        if (hasText(config.issuer())) {
            builder.requireIssuer(config.issuer());
        }
        if (hasText(config.audience())) {
            builder.requireAudience(config.audience());
        }
        if (config.clockSkew() != null && !config.clockSkew().isNegative()) {
            builder.clockSkewSeconds(config.clockSkew().toSeconds());
        }

        this.parser = builder.build();
    }

    @Override
    public UserContext validate(String token) throws InvalidTokenException {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token must not be null or blank");
        }
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ExpiredTokenException("Token has expired: " + ex.getMessage(), ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Token is invalid: " + ex.getMessage(), ex);
        }
        return toUserContext(claims);
    }

    private UserContext toUserContext(Claims claims) {
        return UserContext.builder()
                .userId(claims.getSubject())
                .username(asString(claims.get(config.usernameClaim())))
                .email(asString(claims.get(config.emailClaim())))
                .roles(extractRoles(claims.get(config.rolesClaim())))
                .tenantId(asString(claims.get(config.tenantClaim())))
                .attributes(extractAttributes(claims))
                .build();
    }

    /**
     * Extracts roles from a claim value that may either be a collection (JSON array) or a
     * single comma and/or whitespace separated string.
     */
    private static Set<String> extractRoles(Object rawRoles) {
        if (rawRoles instanceof Collection<?> collection) {
            Set<String> roles = new LinkedHashSet<>();
            for (Object role : collection) {
                if (role != null && !role.toString().isBlank()) {
                    roles.add(role.toString().trim());
                }
            }
            return roles;
        }
        if (rawRoles instanceof String text) {
            Set<String> roles = new LinkedHashSet<>();
            for (String role : text.split("[,\\s]+")) {
                if (!role.isBlank()) {
                    roles.add(role.trim());
                }
            }
            return roles;
        }
        return Set.of();
    }

    private Map<String, Object> extractAttributes(Claims claims) {
        Set<String> mappedClaims = new LinkedHashSet<>(REGISTERED_CLAIMS);
        mappedClaims.add(config.rolesClaim());
        mappedClaims.add(config.usernameClaim());
        mappedClaims.add(config.emailClaim());
        mappedClaims.add(config.tenantClaim());

        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (entry.getValue() != null && !mappedClaims.contains(entry.getKey())) {
                attributes.put(entry.getKey(), entry.getValue());
            }
        }
        // Expose the token id so revocation checks (TokenBlacklist) can address
        // this exact token.
        if (claims.getId() != null) {
            attributes.put(TokenBlacklist.TOKEN_ID_ATTRIBUTE, claims.getId());
        }
        return attributes;
    }

    /**
     * Parses an X.509 (SubjectPublicKeyInfo) PEM encoded RSA public key.
     */
    private static PublicKey parseRsaPublicKey(String pem) {
        String base64 = pem
                .replace(PEM_PUBLIC_KEY_BEGIN, "")
                .replace(PEM_PUBLIC_KEY_END, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unable to parse the configured RSA public key. Provide an X.509 "
                            + "SubjectPublicKeyInfo PEM block delimited by '" + PEM_PUBLIC_KEY_BEGIN
                            + "' and '" + PEM_PUBLIC_KEY_END + "': " + ex.getMessage(), ex);
        }
    }

    private static String asString(Object value) {
        return (value == null) ? null : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
