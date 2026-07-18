package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokensTest {

    @Test
    void generatesUniqueUrlSafeTokens() {
        String token = RefreshTokens.generate();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(RefreshTokens.generate()).isNotEqualTo(token);
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        String token = RefreshTokens.generate();
        assertThat(RefreshTokens.hash(token))
                .hasSize(64)
                .isEqualTo(RefreshTokens.hash(token))
                .isNotEqualTo(RefreshTokens.hash(RefreshTokens.generate()));
    }
}
