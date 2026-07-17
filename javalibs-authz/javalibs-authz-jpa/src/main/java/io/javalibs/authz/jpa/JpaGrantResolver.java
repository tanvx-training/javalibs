package io.javalibs.authz.jpa;

import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default database-backed {@link GrantResolver} reading the {@code authz_role_grant} table.
 *
 * <p>Each row is translated into a {@link ResolvedGrant} by joining the grant's scope with its
 * role's permission codes. A grant whose {@code scopeType}/{@code scopeId} columns are the empty
 * string is the global-grant sentinel used by the schema (see {@link AuthzRoleGrantEntity}) and
 * is translated to {@link Scope#GLOBAL}.</p>
 */
public class JpaGrantResolver implements GrantResolver {

    private final AuthzRoleGrantRepository grantRepository;

    /**
     * Creates a resolver backed by the given repository.
     *
     * @param grantRepository the repository used to load grants, must not be null
     */
    public JpaGrantResolver(AuthzRoleGrantRepository grantRepository) {
        this.grantRepository = Objects.requireNonNull(grantRepository);
    }

    /**
     * Loads every grant of the given subject, with each role's permission codes already
     * joined in, translating the empty-string scope sentinel to {@link Scope#GLOBAL}.
     *
     * @param subject the subject to resolve grants for
     * @return the subject's grants, never {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResolvedGrant> resolveGrants(Subject subject) {
        return grantRepository.findWithRoleBySubject(subject.type(), subject.id()).stream()
                .map(grant -> new ResolvedGrant(
                        toScope(grant),
                        Set.copyOf(grant.getRole().getPermissions())))
                .toList();
    }

    private static Scope toScope(AuthzRoleGrantEntity grant) {
        return grant.getScopeType().isEmpty()
                ? Scope.GLOBAL
                : Scope.of(grant.getScopeType(), grant.getScopeId());
    }
}
