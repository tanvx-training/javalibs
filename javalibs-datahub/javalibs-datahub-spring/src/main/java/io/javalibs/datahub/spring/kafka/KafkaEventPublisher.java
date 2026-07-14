package io.javalibs.datahub.spring.kafka;

import io.javalibs.datahub.DatahubException;
import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * {@link EventPublisher} backed by a Spring {@link KafkaTemplate}.
 *
 * <p>Each envelope is sent as the record value, keyed by the envelope's
 * {@link EventEnvelope#partitionKey() partitionKey} (which may be {@code null}). The
 * standard metadata is propagated as Kafka record headers ({@code x-event-id},
 * {@code x-event-type}, {@code x-correlation-id}, {@code x-source}; {@code null}
 * values are skipped) together with all custom {@link EventEnvelope#headers()}.</p>
 *
 * <p>{@link #publish(String, EventEnvelope)} blocks until the broker acknowledges the
 * record or the configured send timeout elapses, translating any failure into a
 * {@link DatahubException}. {@link #publishAsync(String, EventEnvelope)} returns the
 * underlying send future mapped to {@code Void}.</p>
 *
 * <p>Note: serializing the envelope as JSON requires configuring a JSON-capable value
 * serializer on the producer, e.g.
 * {@code spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer}.</p>
 */
public class KafkaEventPublisher implements EventPublisher {

  /** Kafka header carrying {@link EventEnvelope#eventId()}. */
  public static final String HEADER_EVENT_ID = "x-event-id";
  /** Kafka header carrying {@link EventEnvelope#eventType()}. */
  public static final String HEADER_EVENT_TYPE = "x-event-type";
  /** Kafka header carrying {@link EventEnvelope#correlationId()}. */
  public static final String HEADER_CORRELATION_ID = "x-correlation-id";
  /** Kafka header carrying {@link EventEnvelope#source()}. */
  public static final String HEADER_SOURCE = "x-source";

  private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(30);

  private final KafkaTemplate<String, Object> template;
  private final Duration sendTimeout;

  /**
   * Creates a publisher using the default 30 second send timeout for the blocking
   * {@link #publish(String, EventEnvelope)} call.
   *
   * @param template the Kafka template to send with
   */
  public KafkaEventPublisher(KafkaTemplate<String, Object> template) {
    this(template, DEFAULT_SEND_TIMEOUT);
  }

  /**
   * Creates a publisher with an explicit send timeout for the blocking
   * {@link #publish(String, EventEnvelope)} call.
   *
   * @param template    the Kafka template to send with
   * @param sendTimeout maximum time to wait for the broker acknowledgment
   */
  public KafkaEventPublisher(KafkaTemplate<String, Object> template, Duration sendTimeout) {
    this.template = Objects.requireNonNull(template, "template must not be null");
    this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout must not be null");
  }

  @Override
  public void publish(String topic, EventEnvelope<?> event) {
    try {
      send(topic, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new DatahubException(
          "Interrupted while publishing event " + event.eventId() + " to topic " + topic, ex);
    } catch (ExecutionException ex) {
      throw new DatahubException(
          "Failed to publish event " + event.eventId() + " to topic " + topic,
          ex.getCause() != null ? ex.getCause() : ex);
    } catch (TimeoutException ex) {
      throw new DatahubException(
          "Timed out after " + sendTimeout + " publishing event " + event.eventId()
              + " to topic " + topic, ex);
    }
  }

  @Override
  public CompletableFuture<Void> publishAsync(String topic, EventEnvelope<?> event) {
    try {
      return send(topic, event);
    } catch (DatahubException ex) {
      return CompletableFuture.failedFuture(ex);
    }
  }

  private CompletableFuture<Void> send(String topic, EventEnvelope<?> event) {
    ProducerRecord<String, Object> record = toRecord(topic, event);
    try {
      return template.send(record).thenApply(result -> null);
    } catch (RuntimeException ex) {
      throw new DatahubException(
          "Failed to publish event " + event.eventId() + " to topic " + topic, ex);
    }
  }

  private ProducerRecord<String, Object> toRecord(String topic, EventEnvelope<?> event) {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, event.partitionKey(), event);
    addHeader(record, HEADER_EVENT_ID, event.eventId());
    addHeader(record, HEADER_EVENT_TYPE, event.eventType());
    addHeader(record, HEADER_CORRELATION_ID, event.correlationId());
    addHeader(record, HEADER_SOURCE, event.source());
    event.headers().forEach((name, value) -> addHeader(record, name, value));
    return record;
  }

  private static void addHeader(ProducerRecord<String, Object> record, String name, String value) {
    if (value != null) {
      record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
