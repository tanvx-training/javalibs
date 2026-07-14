package io.javalibs.security.test;

import io.javalibs.security.UserContext;

/**
 * Ready-made {@link UserContext} fixtures for unit tests that need an authenticated user
 * without going through token validation.
 */
public final class TestUserContexts {

    private TestUserContexts() {
    }

    /**
     * Returns an administrator fixture ({@code ADMIN} role).
     *
     * @return a user context with the {@code ADMIN} role
     */
    public static UserContext admin() {
        return UserContext.builder()
                .userId("test-admin")
                .username("admin")
                .email("admin@test.local")
                .roles("ADMIN")
                .build();
    }

    /**
     * Returns a regular user fixture ({@code USER} role).
     *
     * @return a user context with the {@code USER} role
     */
    public static UserContext user() {
        return UserContext.builder()
                .userId("test-user")
                .username("user")
                .email("user@test.local")
                .roles("USER")
                .build();
    }

    /**
     * Returns a fixture carrying exactly the given roles.
     *
     * @param roles the roles to grant
     * @return a user context with the given roles
     */
    public static UserContext withRoles(String... roles) {
        return UserContext.builder()
                .userId("test-user")
                .username("user")
                .roles(roles)
                .build();
    }
}
