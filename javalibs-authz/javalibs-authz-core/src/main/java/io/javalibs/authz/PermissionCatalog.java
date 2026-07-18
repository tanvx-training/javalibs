package io.javalibs.authz;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Optional catalog of every permission code the application defines. When exposed as a
 * Spring bean, authorization write operations through {@code AuthzManagementService} will
 * fail fast on unknown permission codes instead of silently storing typos or invalid
 * permission references.
 *
 * <p>This class maintains an immutable set of valid permission codes and provides validation
 * methods to ensure only registered codes are used throughout the authorization system.
 * The catalog is initialized with a collection of valid permission codes; empty or blank
 * codes are rejected.
 *
 * <p>Example usage:
 * <pre>
 *   PermissionCatalog catalog = new PermissionCatalog(Set.of(
 *       "issue.read", "issue.update", "issue.delete", "project.admin"
 *   ));
 *   catalog.requireKnown(List.of("issue.read", "issue.update"));
 * </pre>
 */
public final class PermissionCatalog {

    private final Set<String> codes;

    /**
     * Constructs a {@code PermissionCatalog} with the provided collection of permission codes.
     *
     * <p>The provided codes are validated: the collection must not be empty, and each code
     * must not be null or blank. Codes are trimmed and stored in an immutable set.
     *
     * @param codes the collection of valid permission codes to register; must not be null,
     *              empty, or contain null/blank values
     * @throws IllegalArgumentException if the codes collection is null, empty, or contains
     *                                  null or blank values
     */
    public PermissionCatalog(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("PermissionCatalog requires at least one code");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Permission codes must not be null or blank");
            }
            copy.add(code.trim());
        }
        this.codes = Set.copyOf(copy);
    }

    /**
     * Returns all registered permission codes in an unmodifiable set.
     *
     * @return an immutable set containing all registered permission codes
     */
    public Set<String> codes() {
        return codes;
    }

    /**
     * Checks whether the specified permission code is registered in this catalog.
     *
     * @param code the permission code to check; may be null
     * @return {@code true} if the code is registered and non-null; {@code false} otherwise
     */
    public boolean contains(String code) {
        return code != null && codes.contains(code);
    }

    /**
     * Validates that every permission code in the provided collection is registered in
     * this catalog.
     *
     * <p>If one or more codes are not registered, an {@link UnknownPermissionException} is
     * thrown with a message listing the unknown codes and the full set of registered codes.
     *
     * @param candidates the collection of permission codes to validate; must not be null
     * @throws UnknownPermissionException if any of the provided codes are not registered
     *                                    in this catalog
     */
    public void requireKnown(Collection<String> candidates) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!contains(candidate)) {
                unknown.add(candidate);
            }
        }
        if (!unknown.isEmpty()) {
            throw new UnknownPermissionException(
                    "Unknown permission codes " + unknown + ". Registered codes: " + codes
                            + ". Add them to the PermissionCatalog bean or fix the typo.");
        }
    }
}
