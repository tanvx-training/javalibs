package io.javalibs.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JwtValidationConfig} defaults and validation.
 */
class JwtValidationConfigTest {

    @Test
    void appliesDocumentedDefaults() {
        JwtValidationConfig config = JwtValidationConfig.builder()
                .hmacSecret("0123456789abcdef0123456789abcdef")
                .build();

        assertThat(config.clockSkew()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.rolesClaim()).isEqualTo("roles");
        assertThat(config.usernameClaim()).isEqualTo("preferred_username");
        assertThat(config.emailClaim()).isEqualTo("email");
        assertThat(config.tenantClaim()).isEqualTo("tenant");
    }

    @Test
    void normalizesNullOptionalValuesToDefaults() {
        JwtValidationConfig config = new JwtValidationConfig(
                "0123456789abcdef0123456789abcdef", null, null, null,
                null, null, null, null, null);

        assertThat(config.clockSkew()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.rolesClaim()).isEqualTo("roles");
    }

    @Test
    void requiresAtLeastOneKeySource() {
        assertThatThrownBy(() -> JwtValidationConfig.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one of");
    }

    @Test
    void acceptsRsaOnlyConfiguration() {
        JwtValidationConfig config = JwtValidationConfig.builder()
                .rsaPublicKeyPem("-----BEGIN PUBLIC KEY-----\nabc\n-----END PUBLIC KEY-----")
                .build();

        assertThat(config.hmacSecret()).isNull();
        assertThat(config.rsaPublicKeyPem()).isNotBlank();
    }
}
