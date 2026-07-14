package io.javalibs.ddd.spring;

import io.javalibs.ddd.DomainEvent;
import io.javalibs.ddd.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link DomainEventPublisher} backed by Spring's in-memory
 * {@link ApplicationEventPublisher}.
 *
 * <p>Listeners subscribe with {@code @EventListener} (or
 * {@code @TransactionalEventListener} to react only after commit):
 *
 * <pre>{@code
 * @TransactionalEventListener
 * void on(OrderPlaced event) { ... }
 * }</pre>
 */
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher delegate;

    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        log.debug("Publishing domain event {} ({})", event.eventType(), event.eventId());
        delegate.publishEvent(event);
    }
}
