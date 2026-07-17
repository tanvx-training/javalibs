package io.javalibs.authz.jpa;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative write API over the authz_* tables: users, groups, roles and grants.
 * When a {@link PermissionCatalog} is supplied every role write is validated against it;
 * when an {@link AuthzCacheInvalidator} is supplied the relevant cache entries are evicted
 * after each mutation. Expose REST endpoints over this service in the application as needed.
 *
 * <p>Both the {@link PermissionCatalog} and the {@link AuthzCacheInvalidator} are optional
 * collaborators (either may be {@code null}). Passing {@code null} for the catalog disables
 * permission-code validation on role creation and on {@link #validateRolesAgainstCatalog()};
 * passing {@code null} for the invalidator simply means grant and membership mutations do not
 * evict any cache, which is correct when no caching layer is configured.</p>
 */
public class AuthzManagementService {

    private final AuthzUserRepository userRepository;
    private final AuthzGroupRepository groupRepository;
    private final AuthzGroupMemberRepository memberRepository;
    private final AuthzRoleRepository roleRepository;
    private final AuthzRoleGrantRepository grantRepository;
    private final PermissionCatalog permissionCatalog;   // nullable
    private final AuthzCacheInvalidator cacheInvalidator; // nullable

    /**
     * Constructs the service over the given repositories and optional collaborators.
     *
     * @param userRepository repository for {@code authz_user} rows, must not be null
     * @param groupRepository repository for {@code authz_group} rows, must not be null
     * @param memberRepository repository for {@code authz_group_member} rows, must not be null
     * @param roleRepository repository for {@code authz_role} rows, must not be null
     * @param grantRepository repository for {@code authz_role_grant} rows, must not be null
     * @param permissionCatalog the catalog used to validate role permission codes, or
     *                          {@code null} to skip validation
     * @param cacheInvalidator the hook used to evict authorization caches after mutations,
     *                         or {@code null} if no caching layer is configured
     * @throws NullPointerException if any repository argument is null
     */
    public AuthzManagementService(AuthzUserRepository userRepository,
            AuthzGroupRepository groupRepository,
            AuthzGroupMemberRepository memberRepository,
            AuthzRoleRepository roleRepository,
            AuthzRoleGrantRepository grantRepository,
            PermissionCatalog permissionCatalog,
            AuthzCacheInvalidator cacheInvalidator) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.groupRepository = Objects.requireNonNull(groupRepository);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.grantRepository = Objects.requireNonNull(grantRepository);
        this.permissionCatalog = permissionCatalog;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * Creates a user; the caller supplies the already-hashed password.
     *
     * <p>The primary key, creation timestamp and last-modified timestamp are assigned
     * automatically by {@link AuthzUserEntity} on first persist.</p>
     *
     * @param username the unique login username
     * @param email the user's email address, or {@code null} if not set
     * @param displayName the human-readable display name, or {@code null} if not set
     * @param passwordHash the already-hashed password; never pass a plaintext password here
     * @param enabled whether the account should be enabled
     * @return the id of the newly created user
     */
    @Transactional
    public UUID createUser(String username, String email, String displayName,
            String passwordHash, boolean enabled) {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        user.setEnabled(enabled);
        return userRepository.save(user).getId();
    }

    /**
     * Creates a group.
     *
     * @param name the unique group name
     * @param description the group description, or {@code null} if not set
     * @return the id of the newly created group
     */
    @Transactional
    public UUID createGroup(String name, String description) {
        AuthzGroupEntity group = new AuthzGroupEntity();
        group.setName(name);
        group.setDescription(description);
        return groupRepository.save(group).getId();
    }

    /**
     * Adds a user to a group (no-op when already a member).
     *
     * <p>Regardless of whether a new membership row was created, the user's cache entries
     * are evicted so any cached group memberships or grants are refreshed on next lookup.</p>
     *
     * @param userId the id of the user to add
     * @param groupId the id of the group to add the user to
     */
    @Transactional
    public void addUserToGroup(UUID userId, UUID groupId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            AuthzGroupMemberEntity member = new AuthzGroupMemberEntity();
            member.setGroupId(groupId);
            member.setUserId(userId);
            memberRepository.save(member);
        }
        evictUser(userId);
    }

    /**
     * Removes a user from a group.
     *
     * <p>This is a no-op if the user is not currently a member of the group. Either way,
     * the user's cache entries are evicted so any cached group memberships or grants are
     * refreshed on next lookup.</p>
     *
     * @param userId the id of the user to remove
     * @param groupId the id of the group to remove the user from
     */
    @Transactional
    public void removeUserFromGroup(UUID userId, UUID groupId) {
        memberRepository.deleteByGroupIdAndUserId(groupId, userId);
        evictUser(userId);
    }

    /**
     * Creates a role bundling the given permission codes.
     *
     * <p>When a {@link PermissionCatalog} was supplied to this service, every code in
     * {@code permissions} is validated against it before the role is persisted; unknown
     * codes cause the role creation to fail without writing anything.</p>
     *
     * @param roleKey the unique, stable role key used for lookups (e.g. {@code "issue-viewer"})
     * @param name the human-readable role name
     * @param description the role description, or {@code null} if not set
     * @param permissions the permission codes granted by this role
     * @return the id of the newly created role
     * @throws io.javalibs.authz.UnknownPermissionException if a {@link PermissionCatalog} is
     *         configured and one or more of the given permission codes are not registered in it
     */
    @Transactional
    public UUID createRole(String roleKey, String name, String description,
            Set<String> permissions) {
        if (permissionCatalog != null) {
            permissionCatalog.requireKnown(permissions);
        }
        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissions);
        return roleRepository.save(role).getId();
    }

    /**
     * Grants the role to the subject in the scope; idempotent.
     *
     * <p>If an identical grant (same subject, role and scope) already exists, this method
     * does nothing beyond evicting the subject's cache entries. Otherwise a new grant row is
     * inserted. The scope is normalized to the empty-string sentinel for both {@code scopeType}
     * and {@code scopeId} when {@link Scope#isGlobal()} is {@code true}, matching the database
     * convention used throughout the {@code authz_role_grant} table.</p>
     *
     * @param subject the subject (user or group) to grant the role to
     * @param roleKey the key of the role to grant
     * @param scope the scope the grant applies to, or {@link Scope#GLOBAL} for a global grant
     * @throws IllegalArgumentException if no role with the given key exists
     */
    @Transactional
    public void grantRole(Subject subject, String roleKey, Scope scope) {
        AuthzRoleEntity role = requireRole(roleKey);
        String scopeType = scope.isGlobal() ? "" : scope.type();
        String scopeId = scope.isGlobal() ? "" : scope.id();
        boolean exists = grantRepository
                .findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
                        subject.type(), subject.id(), role.getId(), scopeType, scopeId)
                .isPresent();
        if (!exists) {
            AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
            grant.setSubjectType(subject.type());
            grant.setSubjectId(subject.id());
            grant.setRole(role);
            grant.setScopeType(scopeType);
            grant.setScopeId(scopeId);
            grantRepository.save(grant);
        }
        evictSubject(subject);
    }

    /**
     * Revokes the role grant of the subject in the scope (no-op when absent).
     *
     * <p>Looks up the grant by its full natural key (subject, role, scope); if found it is
     * deleted, otherwise nothing happens. Either way, the subject's cache entries are evicted
     * so any cached grants are refreshed on next lookup.</p>
     *
     * @param subject the subject (user or group) to revoke the grant from
     * @param roleKey the key of the role to revoke
     * @param scope the scope the grant applies to, or {@link Scope#GLOBAL} for a global grant
     * @throws IllegalArgumentException if no role with the given key exists
     */
    @Transactional
    public void revokeGrant(Subject subject, String roleKey, Scope scope) {
        AuthzRoleEntity role = requireRole(roleKey);
        String scopeType = scope.isGlobal() ? "" : scope.type();
        String scopeId = scope.isGlobal() ? "" : scope.id();
        grantRepository.findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
                        subject.type(), subject.id(), role.getId(), scopeType, scopeId)
                .ifPresent(grantRepository::delete);
        evictSubject(subject);
    }

    /**
     * Validates every stored role against the catalog; call at startup.
     *
     * <p>When no {@link PermissionCatalog} was supplied to this service, this method does
     * nothing (there is nothing to validate against). Otherwise every role's permission codes
     * are checked; this lets the application fail fast at boot time if a role in the database
     * references a permission code that no longer exists in the codebase, rather than
     * discovering the mismatch later at authorization time.</p>
     *
     * @throws io.javalibs.authz.UnknownPermissionException if a {@link PermissionCatalog} is
     *         configured and any stored role references a permission code that is not
     *         registered in it
     */
    @Transactional(readOnly = true)
    public void validateRolesAgainstCatalog() {
        if (permissionCatalog == null) {
            return;
        }
        for (AuthzRoleEntity role : roleRepository.findAllWithPermissions()) {
            permissionCatalog.requireKnown(role.getPermissions());
        }
    }

    /**
     * Looks up a role by its key, failing fast when it does not exist.
     *
     * @param roleKey the role key to look up
     * @return the matching role entity
     * @throws IllegalArgumentException if no role with the given key exists
     */
    private AuthzRoleEntity requireRole(String roleKey) {
        return roleRepository.findByRoleKey(roleKey).orElseThrow(
                () -> new IllegalArgumentException("Unknown role key '" + roleKey
                        + "'. Create the role before granting it."));
    }

    /**
     * Evicts the subject's cached grants, if a cache invalidator is configured.
     *
     * @param subject the subject whose cache entries should be evicted
     */
    private void evictSubject(Subject subject) {
        if (cacheInvalidator != null) {
            cacheInvalidator.evictSubject(subject);
        }
    }

    /**
     * Evicts the user's cached group memberships and grants, if a cache invalidator is
     * configured.
     *
     * @param userId the id of the user whose cache entries should be evicted
     */
    private void evictUser(UUID userId) {
        if (cacheInvalidator != null) {
            cacheInvalidator.evictUser(userId.toString());
        }
    }
}
