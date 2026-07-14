package io.javalibs.datahub.spring.idempotency;

import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience wrapper turning any event listener into an idempotent one.
 *
 * <p>Call inside the listener's database transaction so the dedup marker and
 * the business writes commit atomically; when the action throws, the whole
 * transaction (marker included) rolls back and redelivery retries cleanly:
 *
 * <pre>{@code
 * @KafkaListener(topics = "orders.events")
 * @Transactional
 * public void onOrderEvent(EventEnvelope<JsonNode> envelope) {
 *   idempotentProcessor.process("OrderEventsListener", envelope, () -> {
 *     // business logic — runs at most once per eventId
 *   });
 * }
 * }</pre>
 */
public class IdempotentEventProcessor {

  private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

  private final ProcessedEventStore store;

  public IdempotentEventProcessor(ProcessedEventStore store) {
    this.store = store;
  }

  /**
   * Runs the action unless the event was already processed by this handler.
   *
   * @param handlerName stable logical name of the consumer
   * @param envelope    the delivered event
   * @param action      the business side effects
   * @return {@code true} when the action ran; {@code false} when the event was a
   *     duplicate and was skipped
   */
  public boolean process(String handlerName, EventEnvelope<?> envelope, Runnable action) {
    if (!store.markProcessed(handlerName, envelope.eventId())) {
      log.debug("Duplicate event {} ({}) skipped by handler {}",
          envelope.eventId(), envelope.eventType(), handlerName);
      return false;
    }
    action.run();
    return true;
  }
}
