package io.javalibs.security.issuer;

import io.javalibs.security.JwtTokenValidator;
import io.javalibs.security.JwtValidationConfig;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TokenIssuerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuedTokenValidatesWithSecurityCoreValidator() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .issuer("https://auth.example.com")
                .build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue("u1", "alice", "alice@example.com");

        JwtTokenValidator validator = new JwtTokenValidator(JwtValidationConfig.builder()
                .hmacSecret(SECRET)
                .issuer("https://auth.example.com")
                .build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.userId()).isEqualTo("u1");
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.attributes()).containsEntry(
                TokenBlacklist.TOKEN_ID_ATTRIBUTE, issued.tokenId());
    }

    @Test
    void appliesConfiguredTtl() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .accessTokenTtl(Duration.ofMinutes(5))
                .build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(issuer.issue("u1", null, null).expiresAt())
                .isEqualTo(now.plus(Duration.ofMinutes(5)));
    }

    @Test
    void configRejectsMissingKeysAndShortSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JwtIssuerConfig.builder().build());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JwtIssuerConfig.builder().hmacSecret("short").build());
    }
}
