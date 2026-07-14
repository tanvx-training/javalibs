package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UserContextHolder}.
 */
class UserContextHolderTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentIsEmptyWithoutAuthentication() {
        assertThat(UserContextHolder.current()).isEmpty();
    }

    @Test
    void currentIsEmptyForForeignPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("someone", "n/a", null));

        assertThat(UserContextHolder.current()).isEmpty();
    }

    @Test
    void currentReturnsUserContextPrincipal() {
        UserContext user = UserContext.builder().userId("u1").roles("USER").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UserContextAuthenticationToken(user));

        assertThat(UserContextHolder.current()).contains(user);
        assertThat(UserContextHolder.require()).isSameAs(user);
    }

    @Test
    void requireThrowsWhenAbsent() {
        assertThatThrownBy(UserContextHolder::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No authenticated UserContext");
    }
}
