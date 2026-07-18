package io.javalibs.authz;

import java.util.Objects;
import java.util.Set;

/**
 * A role grant already joined with its role's permissions: "these permissions apply in
 * this scope". Produced by a {@link GrantResolver}.
 */
public record ResolvedGrant(Scope scope, Set<String> permissions) {

    public ResolvedGrant {
        Objects.requireNonNull(scope, "scope must not be null");
        permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
    }

    /** Returns whether this grant applies to the requested scope. */
    public boolean appliesTo(Scope requested) {
        return scope.isGlobal() || scope.equals(requested);
    }
}
