package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerConfigTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rolesClaimDefaultsToRoles() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        assertThat(config.rolesClaim()).isEqualTo("roles");
    }

    @Test
    void rolesClaimCanBeCustomised() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .rolesClaim("authorities")
                .build();
        assertThat(config.rolesClaim()).isEqualTo("authorities");
    }

    @Test
    void blankRolesClaimFallsBackToDefault() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .rolesClaim("  ")
                .build();
        assertThat(config.rolesClaim()).isEqualTo("roles");
    }
}
