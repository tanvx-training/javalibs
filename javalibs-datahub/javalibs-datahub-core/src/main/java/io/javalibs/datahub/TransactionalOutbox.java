package io.javalibs.datahub;

/**
 * Transactional Outbox contract.
 *
 * <p>Writing business state to the database and publishing an event to a broker
 * are two systems that cannot be updated atomically ("dual write"): the database
 * commit may succeed while the broker publish fails (or vice versa), silently
 * de-synchronizing the system. The outbox pattern fixes this by writing the
 * event into an outbox table <em>inside the same database transaction</em> as
 * the business writes; a background relay then reads committed rows and
 * publishes them to the broker with at-least-once semantics.
 *
 * <p>Usage inside an application service:
 *
 * <pre>{@code
 * @Transactional
 * public void placeOrder(PlaceOrderCommand cmd) {
 *   Order order = orderRepository.save(Order.place(cmd));
 *   outbox.enqueue("orders.events",
 *       EventEnvelope.of("OrderPlaced", "orders-service", OrderPlacedEvent.from(order)));
 * }
 * }</pre>
 *
 * <p>Because delivery is at-least-once, consumers must be idempotent — see
 * {@link ProcessedEventStore}.
 */
public interface TransactionalOutbox {

  /**
   * Records the event for asynchronous publication to the given topic. Must be
   * called inside the database transaction that persists the related business
   * state; implementations reject calls made outside a transaction.
   *
   * @param topic destination topic
   * @param event the event to publish after commit
   */
  void enqueue(String topic, EventEnvelope<?> event);
}
