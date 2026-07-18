package io.javalibs.authz;

import java.util.List;

/**
 * SPI: loads every grant of a single subject, with the role's permissions already joined in.
 * Implementations must be safe to call concurrently. javalibs-authz-jpa ships the default
 * database-backed implementation; applications with their own schema implement this instead.
 */
@FunctionalInterface
public interface GrantResolver {

    /** Returns all grants of the subject, never {@code null}. */
    List<ResolvedGrant> resolveGrants(Subject subject);
}
