package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRoleGrantEntity}. */
public interface AuthzRoleGrantRepository extends JpaRepository<AuthzRoleGrantEntity, UUID> {

    /**
     * Returns every grant for the given subject, with the granted role and its
     * permission codes fetched eagerly to avoid N+1 queries.
     *
     * @param subjectType the kind of subject (user or group)
     * @param subjectId the subject's stable identifier
     * @return the subject's grants, with role and permissions loaded
     */
    @Query("select distinct g from AuthzRoleGrantEntity g "
            + "join fetch g.role r left join fetch r.permissions "
            + "where g.subjectType = :subjectType and g.subjectId = :subjectId")
    List<AuthzRoleGrantEntity> findWithRoleBySubject(
            @Param("subjectType") SubjectType subjectType,
            @Param("subjectId") String subjectId);

    /**
     * Finds a single grant by its full natural key (subject, role, and scope).
     *
     * @param subjectType the kind of subject (user or group)
     * @param subjectId the subject's stable identifier
     * @param roleId the granted role's id
     * @param scopeType the scope type, or {@code ""} for a global grant
     * @param scopeId the scope id, or {@code ""} for a global grant
     * @return the matching grant, or empty if none exists
     */
    Optional<AuthzRoleGrantEntity> findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
            SubjectType subjectType, String subjectId, UUID roleId, String scopeType,
            String scopeId);
}
