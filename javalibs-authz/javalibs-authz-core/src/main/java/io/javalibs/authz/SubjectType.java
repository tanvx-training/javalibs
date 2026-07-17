package io.javalibs.authz;

/**
 * The kind of subject a role can be granted to.
 *
 * <p>Subjects are identified by a stable string id: for users the JWT {@code sub} claim,
 * for groups the group id.</p>
 */
public enum SubjectType {
    /** A user subject. */
    USER,
    /** A group subject. */
    GROUP
}
