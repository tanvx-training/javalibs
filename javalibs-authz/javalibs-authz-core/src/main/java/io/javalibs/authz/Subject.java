package io.javalibs.authz;

import java.util.Objects;

/**
 * The receiver of a role grant: a user or a group, identified by a stable string id.
 *
 * <p>For users, the id is typically the JWT {@code sub} claim. For groups, the id is the group
 * identifier within the organization or tenant. Both {@code type} and {@code id} are required
 * and validated on construction.</p>
 *
 * @param type the kind of subject (user or group), must not be null
 * @param id   the unique identifier, must not be null or blank
 */
public record Subject(SubjectType type, String id) {

    public Subject {
        Objects.requireNonNull(type, "type must not be null");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Subject id must not be null or blank");
        }
    }

    /**
     * Creates a user subject with the given user id.
     *
     * @param userId the user's unique identifier, must not be blank
     * @return a user subject
     * @throws IllegalArgumentException if userId is null or blank
     */
    public static Subject user(String userId) {
        return new Subject(SubjectType.USER, userId);
    }

    /**
     * Creates a group subject with the given group id.
     *
     * @param groupId the group's unique identifier, must not be blank
     * @return a group subject
     * @throws IllegalArgumentException if groupId is null or blank
     */
    public static Subject group(String groupId) {
        return new Subject(SubjectType.GROUP, groupId);
    }
}
