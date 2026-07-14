package io.javalibs.ddd;

import java.util.Objects;

/**
 * Base class for DDD entities: objects defined by identity rather than by their
 * attributes.
 *
 * <p>Equality is based on the concrete class and the identifier. Entities with a
 * {@code null} identifier (not yet persisted) are only equal to themselves.
 *
 * @param <ID> identifier type
 */
public abstract class Entity<ID> {

    /** Returns the identity of this entity; may be {@code null} before first persistence. */
    public abstract ID getId();

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Entity<?> that = (Entity<?>) other;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        // Constant per class so the hash stays stable when the id is assigned on persist.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + getId() + "]";
    }
}
