package io.javalibs.ddd;

import java.util.Collection;

/**
 * Publishes domain events to interested listeners.
 *
 * <p>The contract lives in the core module so domain/application code stays
 * framework-free; javalibs-ddd-spring provides an implementation backed by
 * Spring's {@code ApplicationEventPublisher}.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(Collection<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }

    /**
     * Pulls all recorded events from the aggregate (clearing them) and publishes
     * each one. Call after the aggregate has been persisted successfully.
     */
    default void publishFrom(AggregateRoot<?> aggregate) {
        publishAll(aggregate.pullDomainEvents());
    }
}
