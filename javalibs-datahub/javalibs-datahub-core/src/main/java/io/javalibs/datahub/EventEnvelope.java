package io.javalibs.datahub;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, transport-agnostic envelope wrapping a domain event payload with the
 * standardized metadata every event exchanged between javalibs services must carry.
 *
 * <p>Use {@link #of(String, String, Object)} for the common case (generated event id,
 * current timestamp, no correlation data) or {@link #builder()} for full control over
 * every field.</p>
 *
 * @param eventId       unique identifier of this event occurrence; never {@code null}
 *                      when created through {@link #of(String, String, Object)} or the
 *                      {@link Builder}
 * @param eventType     logical type of the event (e.g. {@code "order.created"});
 *                      required, never blank
 * @param source        logical name of the system that emitted the event; may be
 *                      {@code null}
 * @param occurredAt    instant at which the event occurred; never {@code null} when
 *                      created through the factory or the builder
 * @param correlationId identifier used to correlate this event with the request or
 *                      workflow that triggered it; may be {@code null}
 * @param partitionKey  key used by partitioned transports (e.g. Kafka) to route the
 *                      event; may be {@code null}
 * @param headers       additional transport headers; defensively copied, never
 *                      {@code null}
 * @param payload       the domain event itself; required
 * @param <T>           payload type
 */
public record EventEnvelope<T>(
    String eventId,
    String eventType,
    String source,
    Instant occurredAt,
    String correlationId,
    String partitionKey,
    Map<String, String> headers,
    T payload) {

  /**
   * Validates required fields and defensively copies the mutable {@code headers} map.
   *
   * @throws IllegalArgumentException if {@code eventType} is {@code null} or blank
   * @throws NullPointerException     if {@code payload} is {@code null}
   */
  public EventEnvelope {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("eventType must not be null or blank");
    }
    Objects.requireNonNull(payload, "payload must not be null");
    headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  /**
   * Creates an envelope with a random {@link UUID} event id, {@link Instant#now()} as
   * the occurrence time and no correlation id, partition key or extra headers.
   *
   * @param eventType logical type of the event; required, never blank
   * @param source    logical name of the emitting system; may be {@code null}
   * @param payload   the domain event; required
   * @param <T>       payload type
   * @return a new envelope
   */
  public static <T> EventEnvelope<T> of(String eventType, String source, T payload) {
    return new EventEnvelope<>(
        UUID.randomUUID().toString(), eventType, source, Instant.now(),
        null, null, Map.of(), payload);
  }

  /**
   * Returns a new {@link Builder} for assembling an envelope with full control over
   * every field.
   *
   * @param <T> payload type
   * @return a fresh builder
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Mutable builder for {@link EventEnvelope}. Fields left unset fall back to sensible
   * defaults on {@link #build()}: a random UUID for {@code eventId} and
   * {@link Instant#now()} for {@code occurredAt}.
   *
   * @param <T> payload type
   */
  public static final class Builder<T> {

    private String eventId;
    private String eventType;
    private String source;
    private Instant occurredAt;
    private String correlationId;
    private String partitionKey;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private T payload;

    private Builder() {
    }

    /**
     * Sets a custom event id. Defaults to a random UUID when unset.
     *
     * @param eventId the event id
     * @return this builder
     */
    public Builder<T> eventId(String eventId) {
      this.eventId = eventId;
      return this;
    }

    /**
     * Sets the required logical event type.
     *
     * @param eventType the event type
     * @return this builder
     */
    public Builder<T> eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    /**
     * Sets the logical name of the emitting system.
     *
     * @param source the source system name
     * @return this builder
     */
    public Builder<T> source(String source) {
      this.source = source;
      return this;
    }

    /**
     * Sets a custom occurrence time. Defaults to {@link Instant#now()} when unset.
     *
     * @param occurredAt the occurrence instant
     * @return this builder
     */
    public Builder<T> occurredAt(Instant occurredAt) {
      this.occurredAt = occurredAt;
      return this;
    }

    /**
     * Sets the correlation id linking this event to the triggering request/workflow.
     *
     * @param correlationId the correlation id
     * @return this builder
     */
    public Builder<T> correlationId(String correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    /**
     * Sets the partition key used by partitioned transports such as Kafka.
     *
     * @param partitionKey the partition key
     * @return this builder
     */
    public Builder<T> partitionKey(String partitionKey) {
      this.partitionKey = partitionKey;
      return this;
    }

    /**
     * Adds a single transport header.
     *
     * @param name  header name
     * @param value header value
     * @return this builder
     */
    public Builder<T> header(String name, String value) {
      this.headers.put(name, value);
      return this;
    }

    /**
     * Adds all entries of the given map as transport headers.
     *
     * @param headers headers to add; may be {@code null} (ignored)
     * @return this builder
     */
    public Builder<T> headers(Map<String, String> headers) {
      if (headers != null) {
        this.headers.putAll(headers);
      }
      return this;
    }

    /**
     * Sets the required domain event payload.
     *
     * @param payload the payload
     * @return this builder
     */
    public Builder<T> payload(T payload) {
      this.payload = payload;
      return this;
    }

    /**
     * Builds the envelope, applying defaults for {@code eventId} and
     * {@code occurredAt} when they were not set explicitly.
     *
     * @return the immutable envelope
     * @throws IllegalArgumentException if {@code eventType} is missing or blank
     * @throws NullPointerException     if {@code payload} is missing
     */
    public EventEnvelope<T> build() {
      return new EventEnvelope<>(
          eventId != null ? eventId : UUID.randomUUID().toString(),
          eventType,
          source,
          occurredAt != null ? occurredAt : Instant.now(),
          correlationId,
          partitionKey,
          headers,
          payload);
    }
  }
}
