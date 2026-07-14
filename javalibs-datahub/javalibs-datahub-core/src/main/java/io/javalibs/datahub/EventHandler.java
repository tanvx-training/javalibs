package io.javalibs.datahub;

/**
 * Contract implemented by event consumers to register a typed handler for a single
 * logical event type.
 *
 * <p>Consumer infrastructure (e.g. a Kafka listener) is expected to dispatch each
 * incoming {@link EventEnvelope} to the handler whose {@link #eventType()} matches the
 * envelope's {@link EventEnvelope#eventType()}.</p>
 *
 * @param <T> the payload type this handler understands
 */
public interface EventHandler<T> {

  /**
   * Returns the logical event type this handler subscribes to (e.g.
   * {@code "order.created"}).
   *
   * @return the handled event type; never {@code null}
   */
  String eventType();

  /**
   * Handles a single event of the subscribed type.
   *
   * @param event the incoming event envelope
   */
  void handle(EventEnvelope<T> event);
}
