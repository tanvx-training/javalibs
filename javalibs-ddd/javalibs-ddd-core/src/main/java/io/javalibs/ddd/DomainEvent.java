package io.javalibs.ddd;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in the domain that other parts of the system may react
 * to. Events are immutable facts, named in past tense (e.g. {@code OrderPlaced}).
 */
public interface DomainEvent {

    /** Unique identifier of this event occurrence. */
    UUID eventId();

    /** When the event happened. */
    Instant occurredOn();

    /** Logical event type; defaults to the simple class name (e.g. "OrderPlaced"). */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
