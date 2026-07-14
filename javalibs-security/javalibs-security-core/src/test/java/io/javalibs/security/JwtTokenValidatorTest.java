package io.javalibs.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for {@link JwtTokenValidator}: tokens are minted with JJWT (available on the
 * test classpath through the runtime-scoped jjwt-impl dependency) and validated with the core API.
 */
class JwtTokenValidatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "another-secret-another-secret-another-secret!!";
    private static final String ISSUER = "https://sso.example.com";
    private static final String AUDIENCE = "orders-api";

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtValidationConfig.Builder config() {
        return JwtValidationConfig.builder().hmacSecret(SECRET);
    }

    @Nested
    class ClaimMapping {

        @Test
        void mapsAllStandardClaimsToUserContext() {
            Instant now = Instant.now();
            String token = Jwts.builder()
                    .subject("user-42")
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(3600)))
                    .claim("roles", List.of("ADMIN", "USER"))
                    .claim("preferred_username", "jdoe")
                    .claim("email", "jdoe@example.com")
                    .claim("tenant", "acme")
                    .claim("department", "engineering")
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(
                    config().issuer(ISSUER).audience(AUDIENCE).build());
            UserContext user = validator.validate(token);

            assertThat(user.userId()).isEqualTo("user-42");
            assertThat(user.username()).isEqualTo("jdoe");
            assertThat(user.email()).isEqualTo("jdoe@example.com");
            assertThat(user.tenantId()).isEqualTo("acme");
            assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
            assertThat(user.attributes())
                    .containsEntry("department", "engineering")
                    .doesNotContainKeys("sub", "iss", "aud", "exp", "iat",
                            "roles", "preferred_username", "email", "tenant");
        }

        @Test
        void parsesRolesFromCommaSeparatedString() {
            String token = tokenWithRolesClaim("ADMIN, USER,AUDITOR");

            UserContext user = new JwtTokenValidator(config().build()).validate(token);

            assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER", "AUDITOR");
        }

        @Test
        void parsesRolesFromSpaceSeparatedString() {
            String token = tokenWithRolesClaim("ADMIN USER");

            UserContext user = new JwtTokenValidator(config().build()).validate(token);

            assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        }

        @Test
        void missingRolesClaimYieldsEmptyRoles() {
            String token = Jwts.builder()
                    .subject("user-1")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            UserContext user = new JwtTokenValidator(config().build()).validate(token);

            assertThat(user.roles()).isEmpty();
            assertThat(user.username()).isNull();
        }

        @Test
        void honorsCustomClaimNames() {
            String token = Jwts.builder()
                    .subject("user-9")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("authorities", List.of("MANAGER"))
                    .claim("login", "manager9")
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(config()
                    .rolesClaim("authorities")
                    .usernameClaim("login")
                    .build());
            UserContext user = validator.validate(token);

            assertThat(user.roles()).containsExactly("MANAGER");
            assertThat(user.username()).isEqualTo("manager9");
        }

        private String tokenWithRolesClaim(Object rolesValue) {
            return Jwts.builder()
                    .subject("user-1")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("roles", rolesValue)
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();
        }
    }

    @Nested
    class Rejections {

        @Test
        void expiredTokenThrowsExpiredTokenException() {
            Instant past = Instant.now().minusSeconds(3600);
            String token = Jwts.builder()
                    .subject("user-1")
                    .issuedAt(Date.from(past.minusSeconds(60)))
                    .expiration(Date.from(past))
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(
                    config().clockSkew(Duration.ZERO).build());

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(ExpiredTokenException.class);
        }

        @Test
        void expiredTokenWithinClockSkewIsAccepted() {
            Instant justExpired = Instant.now().minusSeconds(10);
            String token = Jwts.builder()
                    .subject("user-1")
                    .expiration(Date.from(justExpired))
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(
                    config().clockSkew(Duration.ofMinutes(2)).build());

            assertThat(validator.validate(token).userId()).isEqualTo("user-1");
        }

        @Test
        void wrongSignatureThrowsInvalidTokenException() {
            String token = Jwts.builder()
                    .subject("user-1")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(key(OTHER_SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(config().build());

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(InvalidTokenException.class)
                    .isNotInstanceOf(ExpiredTokenException.class);
        }

        @Test
        void issuerMismatchThrowsInvalidTokenException() {
            String token = Jwts.builder()
                    .subject("user-1")
                    .issuer("https://evil.example.com")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(config().issuer(ISSUER).build());

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void audienceMismatchThrowsInvalidTokenException() {
            String token = Jwts.builder()
                    .subject("user-1")
                    .audience().add("another-api").and()
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(key(SECRET), Jwts.SIG.HS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(config().audience(AUDIENCE).build());

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void malformedTokenThrowsInvalidTokenException() {
            JwtTokenValidator validator = new JwtTokenValidator(config().build());

            assertThatThrownBy(() -> validator.validate("not-a-jwt"))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void nullOrBlankTokenThrowsInvalidTokenException() {
            JwtTokenValidator validator = new JwtTokenValidator(config().build());

            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(InvalidTokenException.class);
            assertThatThrownBy(() -> validator.validate("  "))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class Configuration {

        @Test
        void shortHmacSecretIsRejectedWithClearMessage() {
            JwtValidationConfig shortSecret = config().hmacSecret("too-short").build();

            assertThatThrownBy(() -> new JwtTokenValidator(shortSecret))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        void missingKeyMaterialIsRejectedByConfig() {
            assertThatThrownBy(() -> JwtValidationConfig.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hmacSecret")
                    .hasMessageContaining("rsaPublicKeyPem");
        }

        @Test
        void garbagePublicKeyPemIsRejectedWithClearMessage() {
            JwtValidationConfig config = JwtValidationConfig.builder()
                    .rsaPublicKeyPem("-----BEGIN PUBLIC KEY-----\nnot base64!!!\n-----END PUBLIC KEY-----")
                    .build();

            assertThatThrownBy(() -> new JwtTokenValidator(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RSA public key");
        }
    }

    @Nested
    class Rsa {

        @Test
        void validatesRsaSignedTokenFromPemPublicKey() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            String pem = PEM_HEADER + "\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(keyPair.getPublic().getEncoded())
                    + "\n" + PEM_FOOTER;

            String token = Jwts.builder()
                    .subject("rsa-user")
                    .claim("roles", List.of("ADMIN"))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                    .compact();

            JwtTokenValidator validator = new JwtTokenValidator(
                    JwtValidationConfig.builder().rsaPublicKeyPem(pem).build());
            UserContext user = validator.validate(token);

            assertThat(user.userId()).isEqualTo("rsa-user");
            assertThat(user.hasRole("ADMIN")).isTrue();
        }

        private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
        private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";
    }
}
