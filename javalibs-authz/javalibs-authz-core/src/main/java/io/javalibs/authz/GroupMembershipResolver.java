package io.javalibs.authz;

import java.util.Set;

/**
 * SPI: resolves the group ids a user belongs to (direct membership only — groups do not nest).
 */
@FunctionalInterface
public interface GroupMembershipResolver {

    /** Returns the ids of the groups the user is a direct member of, never {@code null}. */
    Set<String> groupsOf(String userId);
}
