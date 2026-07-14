package io.javalibs.security.test;

import io.javalibs.security.ExpiredTokenException;
import io.javalibs.security.InvalidTokenException;
import io.javalibs.security.JwtTokenValidator;
import io.javalibs.security.JwtValidationConfig;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that tokens minted by {@link JwtTestFactory} validate through the core
 * {@link JwtTokenValidator} when configured with the same secret.
 */
class JwtTestFactoryTest {

    private static JwtTokenValidator validator() {
        return new JwtTokenValidator(JwtValidationConfig.builder()
                .hmacSecret(JwtTestFactory.DEFAULT_TEST_SECRET)
                .build());
    }

    @Test
    void factoryTokenValidatesThroughCoreValidator() {
        String token = JwtTestFactory.create()
                .subject("user-7")
                .username("jdoe")
                .email("jdoe@example.com")
                .roles("ADMIN", "USER")
                .tenant("acme")
                .claim("department", "engineering")
                .token();

        UserContext user = validator().validate(token);

        assertThat(user.userId()).isEqualTo("user-7");
        assertThat(user.username()).isEqualTo("jdoe");
        assertThat(user.email()).isEqualTo("jdoe@example.com");
        assertThat(user.tenantId()).isEqualTo("acme");
        assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(user.attributes()).containsEntry("department", "engineering");
    }

    @Test
    void defaultsProduceAValidTokenForTestUser() {
        UserContext user = validator().validate(JwtTestFactory.create().token());

        assertThat(user.userId()).isEqualTo("test-user");
        assertThat(user.roles()).isEmpty();
    }

    @Test
    void issuerAndAudienceAreHonored() {
        String token = JwtTestFactory.create()
                .issuer("https://sso.example.com")
                .audience("orders-api")
                .token();

        JwtTokenValidator strictValidator = new JwtTokenValidator(JwtValidationConfig.builder()
                .hmacSecret(JwtTestFactory.DEFAULT_TEST_SECRET)
                .issuer("https://sso.example.com")
                .audience("orders-api")
                .build());

        assertThat(strictValidator.validate(token).userId()).isEqualTo("test-user");
    }

    @Test
    void bearerPrefixesTheToken() {
        String bearer = JwtTestFactory.create().bearer();

        assertThat(bearer).startsWith("Bearer ");
        assertThat(validator().validate(bearer.substring("Bearer ".length())).userId())
                .isEqualTo("test-user");
    }

    @Test
    void pastIssuedAtMintsAnExpiredToken() {
        String expired = JwtTestFactory.create()
                .issuedAt(Instant.now().minus(Duration.ofHours(3)))
                .expiresIn(Duration.ofHours(1))
                .token();

        JwtTokenValidator noSkewValidator = new JwtTokenValidator(JwtValidationConfig.builder()
                .hmacSecret(JwtTestFactory.DEFAULT_TEST_SECRET)
                .clockSkew(Duration.ZERO)
                .build());

        assertThatThrownBy(() -> noSkewValidator.validate(expired))
                .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    void overriddenSecretDoesNotValidateAgainstDefaultSecret() {
        String token = JwtTestFactory.create()
                .secret("another-secret-of-sufficient-length-123456")
                .token();

        assertThatThrownBy(() -> validator().validate(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void fixturesCarryExpectedRoles() {
        assertThat(TestUserContexts.admin().hasRole("ADMIN")).isTrue();
        assertThat(TestUserContexts.user().hasRole("USER")).isTrue();
        assertThat(TestUserContexts.withRoles("A", "B").roles())
                .containsExactlyInAnyOrder("A", "B");
    }
}
