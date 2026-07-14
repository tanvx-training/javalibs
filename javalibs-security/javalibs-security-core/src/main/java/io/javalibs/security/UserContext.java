package io.javalibs.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of the authenticated caller, decoupled from any web or security framework.
 *
 * <p>A {@code UserContext} is typically produced by a {@link TokenValidator} from a verified JWT
 * and then propagated through the application (for example as the Spring Security principal).
 * All collections are defensively copied and unmodifiable; {@code null} collections are
 * normalized to empty ones so callers never have to null-check {@link #roles()} or
 * {@link #attributes()}.</p>
 *
 * @param userId     stable unique identifier of the user (usually the JWT {@code sub} claim)
 * @param username   human readable login / display name (may be {@code null})
 * @param email      e-mail address of the user (may be {@code null})
 * @param roles      granted roles, never {@code null}, unmodifiable
 * @param tenantId   identifier of the tenant the user belongs to (may be {@code null})
 * @param attributes additional token claims that were not mapped to a dedicated field,
 *                   never {@code null}, unmodifiable
 */
public record UserContext(
        String userId,
        String username,
        String email,
        Set<String> roles,
        String tenantId,
        Map<String, Object> attributes) {

    /**
     * Canonical constructor normalizing {@code null} collections to empty ones and taking
     * defensive, unmodifiable copies.
     */
    public UserContext {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Returns whether the user has the given role (exact, case-sensitive match).
     *
     * @param role the role to check, may be {@code null} (returns {@code false})
     * @return {@code true} if the role is present
     */
    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * Returns whether the user has at least one of the given roles.
     *
     * @param candidates candidate roles, {@code null} entries are ignored
     * @return {@code true} if any of the candidate roles is present
     */
    public boolean hasAnyRole(String... candidates) {
        if (candidates == null) {
            return false;
        }
        return Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .anyMatch(roles::contains);
    }

    /**
     * Creates a new {@link Builder} for assembling a {@code UserContext}.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link UserContext}. Not thread-safe.
     */
    public static final class Builder {

        private String userId;
        private String username;
        private String email;
        private final Set<String> roles = new LinkedHashSet<>();
        private String tenantId;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Sets the unique user identifier.
         *
         * @param userId the user id
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets the username.
         *
         * @param username the username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the e-mail address.
         *
         * @param email the e-mail address
         * @return this builder
         */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /**
         * Replaces the accumulated roles with the given collection.
         *
         * @param roles roles to set, {@code null} clears the roles
         * @return this builder
         */
        public Builder roles(Collection<String> roles) {
            this.roles.clear();
            if (roles != null) {
                this.roles.addAll(roles);
            }
            return this;
        }

        /**
         * Replaces the accumulated roles with the given values.
         *
         * @param roles roles to set
         * @return this builder
         */
        public Builder roles(String... roles) {
            return roles((roles == null) ? null : Arrays.asList(roles));
        }

        /**
         * Adds a single role.
         *
         * @param role the role to add, ignored when {@code null}
         * @return this builder
         */
        public Builder role(String role) {
            if (role != null) {
                this.roles.add(role);
            }
            return this;
        }

        /**
         * Sets the tenant identifier.
         *
         * @param tenantId the tenant id
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Replaces the accumulated attributes with the given map.
         *
         * @param attributes attributes to set, {@code null} clears them
         * @return this builder
         */
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        /**
         * Adds a single attribute.
         *
         * @param key   attribute name
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Builds the immutable {@link UserContext}.
         *
         * @return the user context
         */
        public UserContext build() {
            return new UserContext(userId, username, email, roles, tenantId, attributes);
        }
    }
}
