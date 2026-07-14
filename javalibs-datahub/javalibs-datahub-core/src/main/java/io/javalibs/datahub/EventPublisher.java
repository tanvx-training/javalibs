package io.javalibs.datahub;

import java.util.concurrent.CompletableFuture;

/**
 * Transport-agnostic contract for publishing {@link EventEnvelope events} to a named
 * destination (a Kafka topic, a message queue, a log, ...).
 *
 * <p>Application code should depend on this interface only; the concrete transport is
 * chosen at deployment time (e.g. Kafka in production, a logging fallback in local
 * development).</p>
 */
public interface EventPublisher {

  /**
   * Publishes the event to the given destination, blocking until the transport has
   * acknowledged it or an error occurred.
   *
   * @param topic destination name (e.g. Kafka topic)
   * @param event the event to publish
   * @throws DatahubException if the event could not be published
   */
  void publish(String topic, EventEnvelope<?> event);

  /**
   * Publishes the event asynchronously. The default implementation simply wraps the
   * blocking {@link #publish(String, EventEnvelope)} call in an already completed (or
   * already failed) future; transport implementations are encouraged to override it
   * with a truly non-blocking variant.
   *
   * @param topic destination name (e.g. Kafka topic)
   * @param event the event to publish
   * @return a future completing when the transport acknowledged the event, or failing
   *         with the publish error
   */
  default CompletableFuture<Void> publishAsync(String topic, EventEnvelope<?> event) {
    try {
      publish(topic, event);
      return CompletableFuture.completedFuture(null);
    } catch (RuntimeException ex) {
      return CompletableFuture.failedFuture(ex);
    }
  }
}
