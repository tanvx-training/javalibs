package io.javalibs.authz;

/**
 * The scope a permission grant applies to: either {@link #GLOBAL} (the whole system) or a
 * concrete resource identified by a scope type and id, e.g. {@code ("project", "42")}.
 *
 * <p>A {@code Scope} is always either global (both {@code type} and {@code id} are {@code null})
 * or fully specified (both {@code type} and {@code id} are non-blank strings). Partial scopes
 * (only one of {@code type} or {@code id} set) are rejected with {@link IllegalArgumentException}.</p>
 *
 * @param type the scope type (e.g., "project", "organization"), {@code null} for global scope
 * @param id   the scope id (e.g., "42"), {@code null} for global scope
 */
public record Scope(String type, String id) {

    /** Grant applies everywhere. */
    public static final Scope GLOBAL = new Scope(null, null);

    public Scope {
        boolean bothNull = type == null && id == null;
        boolean bothSet = hasText(type) && hasText(id);
        if (!bothNull && !bothSet) {
            throw new IllegalArgumentException(
                    "A scope is either GLOBAL (no type, no id) or a full (type, id) pair; got type="
                            + type + ", id=" + id);
        }
    }

    /**
     * Creates a resource scope with the given type and id.
     *
     * @param type the scope type, must not be blank
     * @param id   the scope id, must not be blank
     * @return a scope for the given type and id
     * @throws IllegalArgumentException if either argument is null or blank
     */
    public static Scope of(String type, String id) {
        return new Scope(type, id);
    }

    /**
     * Returns whether this is the global scope.
     *
     * @return {@code true} if this is the global scope, {@code false} otherwise
     */
    public boolean isGlobal() {
        return type == null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
