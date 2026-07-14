package io.javalibs.ddd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for aggregate roots: the consistency boundary of the domain model
 * and the only entities a {@link Repository} loads and stores.
 *
 * <p>State changes should record {@link DomainEvent}s via
 * {@link #registerEvent(DomainEvent)}. The application layer (or an
 * infrastructure dispatcher) pulls them with {@link #pullDomainEvents()} after a
 * successful persistence operation and publishes them.
 *
 * @param <ID> identifier type
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /** Records a domain event to be published after the aggregate is persisted. */
    protected void registerEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        domainEvents.add(event);
    }

    /** Read-only view of the recorded, not-yet-published events. */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Returns the recorded events and clears the internal list — call exactly once
     * per persistence operation, then publish the returned events.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
