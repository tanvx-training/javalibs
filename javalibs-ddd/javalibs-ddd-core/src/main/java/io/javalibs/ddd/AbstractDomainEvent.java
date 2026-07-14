package io.javalibs.ddd;

import java.time.Instant;
import java.util.UUID;

/**
 * Convenience base class for {@link DomainEvent} implementations that generates
 * the event id and timestamp at construction time.
 */
public abstract class AbstractDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant occurredOn;

    protected AbstractDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredOn = Instant.now();
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }

    @Override
    public String toString() {
        return eventType() + "[eventId=" + eventId + ", occurredOn=" + occurredOn + "]";
    }
}
