package io.javalibs.authz.jpa;

import io.javalibs.authz.GroupMembershipResolver;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default database-backed {@link GroupMembershipResolver}. User ids that are not UUIDs
 * (e.g. subjects minted by an external IdP that have no local user row) resolve to no groups.
 */
public class JpaGroupMembershipResolver implements GroupMembershipResolver {

    private final AuthzGroupMemberRepository memberRepository;

    /**
     * Creates a resolver backed by the given repository.
     *
     * @param memberRepository the repository used to load group memberships, must not be null
     */
    public JpaGroupMembershipResolver(AuthzGroupMemberRepository memberRepository) {
        this.memberRepository = Objects.requireNonNull(memberRepository);
    }

    /**
     * Returns the ids of the groups the given user is a direct member of.
     *
     * <p>If {@code userId} is not a valid UUID, this returns an empty set rather than
     * throwing: the local schema keys memberships by UUID, but subjects can arrive from an
     * external identity provider whose ids are not UUIDs and simply have no local row.</p>
     *
     * @param userId the user id, expected to be a UUID string
     * @return the group ids the user belongs to, or an empty set if {@code userId} is not a UUID
     */
    @Override
    @Transactional(readOnly = true)
    public Set<String> groupsOf(String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return Set.of();
        }
        Set<String> groupIds = new LinkedHashSet<>();
        for (UUID groupId : memberRepository.findGroupIdsByUserId(uuid)) {
            groupIds.add(groupId.toString());
        }
        return groupIds;
    }
}
