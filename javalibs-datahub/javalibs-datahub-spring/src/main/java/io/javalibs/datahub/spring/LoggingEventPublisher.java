package io.javalibs.datahub.spring;

import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EventPublisher} that simply logs each event at INFO level instead of sending
 * it anywhere.
 *
 * <p>Used as a safe fallback so that code depending on {@link EventPublisher} keeps
 * working in local or development environments where no Kafka broker (or other real
 * transport) is available.</p>
 */
public class LoggingEventPublisher implements EventPublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

  @Override
  public void publish(String topic, EventEnvelope<?> event) {
    log.info("published event: topic={}, eventId={}, eventType={}",
        topic, event.eventId(), event.eventType());
  }
}
