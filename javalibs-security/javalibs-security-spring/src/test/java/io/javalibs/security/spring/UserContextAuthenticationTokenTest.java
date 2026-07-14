package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UserContextAuthenticationToken} authority mapping.
 */
class UserContextAuthenticationTokenTest {

    @Test
    void rolesAreMappedToRolePrefixedAuthorities() {
        UserContext user = UserContext.builder().userId("u1").roles("ADMIN", "USER").build();

        UserContextAuthenticationToken token = new UserContextAuthenticationToken(user);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void alreadyPrefixedRolesAreNotDoublePrefixed() {
        UserContext user = UserContext.builder().userId("u1").roles("ROLE_ADMIN", "USER").build();

        UserContextAuthenticationToken token = new UserContextAuthenticationToken(user);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void tokenIsAuthenticatedAndExposesPrincipal() {
        UserContext user = UserContext.builder().userId("u1").username("jdoe").build();

        UserContextAuthenticationToken token = new UserContextAuthenticationToken(user);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getPrincipal()).isSameAs(user);
        assertThat(token.getName()).isEqualTo("jdoe");
        assertThat(token.getCredentials()).isEqualTo("");
    }

    @Test
    void nameFallsBackToUserIdWithoutUsername() {
        UserContext user = UserContext.builder().userId("u1").build();

        assertThat(new UserContextAuthenticationToken(user).getName()).isEqualTo("u1");
    }
}
