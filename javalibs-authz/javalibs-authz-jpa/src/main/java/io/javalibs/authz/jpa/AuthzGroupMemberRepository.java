package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link AuthzGroupMemberEntity}. */
public interface AuthzGroupMemberRepository extends JpaRepository<AuthzGroupMemberEntity, UUID> {

    /**
     * Returns the ids of every group the given user directly belongs to.
     *
     * @param userId the user id
     * @return the group ids the user is a member of
     */
    @Query("select m.groupId from AuthzGroupMemberEntity m where m.userId = :userId")
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);

    /**
     * Removes the membership of the given user in the given group, if present.
     *
     * @param groupId the group id
     * @param userId the user id
     */
    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    /**
     * Checks whether the given user is a direct member of the given group.
     *
     * @param groupId the group id
     * @param userId the user id
     * @return {@code true} if the membership exists
     */
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
