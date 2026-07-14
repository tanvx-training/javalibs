package io.javalibs.datahub.spring.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalibs.datahub.DatahubException;
import io.javalibs.datahub.EventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link KafkaEventPublisher} using a mocked {@link KafkaTemplate}.
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

  @Mock
  private KafkaTemplate<String, Object> template;

  private KafkaEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new KafkaEventPublisher(template, Duration.ofSeconds(1));
  }

  @SuppressWarnings("unchecked")
  private CompletableFuture<SendResult<String, Object>> successFuture() {
    return CompletableFuture.completedFuture(mock(SendResult.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishSendsRecordWithTopicKeyValueAndHeaders() {
    when(template.send(any(ProducerRecord.class))).thenReturn(successFuture());
    EventEnvelope<String> event = EventEnvelope.<String>builder()
        .eventId("evt-1")
        .eventType("order.created")
        .source("order-service")
        .correlationId("corr-1")
        .partitionKey("order-42")
        .header("tenant", "acme")
        .payload("data")
        .build();

    publisher.publish("orders", event);

    ArgumentCaptor<ProducerRecord<String, Object>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(template).send(captor.capture());
    ProducerRecord<String, Object> record = captor.getValue();
    assertThat(record.topic()).isEqualTo("orders");
    assertThat(record.key()).isEqualTo("order-42");
    assertThat(record.value()).isSameAs(event);
    assertThat(headerValue(record, "x-event-id")).isEqualTo("evt-1");
    assertThat(headerValue(record, "x-event-type")).isEqualTo("order.created");
    assertThat(headerValue(record, "x-correlation-id")).isEqualTo("corr-1");
    assertThat(headerValue(record, "x-source")).isEqualTo("order-service");
    assertThat(headerValue(record, "tenant")).isEqualTo("acme");
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishSkipsNullMetadataHeadersAndUsesNullKeyWithoutPartitionKey() {
    when(template.send(any(ProducerRecord.class))).thenReturn(successFuture());
    EventEnvelope<String> event = EventEnvelope.of("order.created", null, "data");

    publisher.publish("orders", event);

    ArgumentCaptor<ProducerRecord<String, Object>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(template).send(captor.capture());
    ProducerRecord<String, Object> record = captor.getValue();
    assertThat(record.key()).isNull();
    assertThat(record.headers().lastHeader("x-correlation-id")).isNull();
    assertThat(record.headers().lastHeader("x-source")).isNull();
    assertThat(headerValue(record, "x-event-id")).isEqualTo(event.eventId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishWrapsSendFailureInDatahubException() {
    RuntimeException cause = new RuntimeException("broker down");
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(cause));
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    assertThatThrownBy(() -> publisher.publish("orders", event))
        .isInstanceOf(DatahubException.class)
        .hasMessageContaining("orders")
        .cause().isSameAs(cause);
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishWrapsTimeoutInDatahubException() {
    when(template.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    publisher = new KafkaEventPublisher(template, Duration.ofMillis(50));
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    assertThatThrownBy(() -> publisher.publish("orders", event))
        .isInstanceOf(DatahubException.class)
        .hasMessageContaining("Timed out");
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishAsyncCompletesWhenSendSucceeds() {
    CompletableFuture<SendResult<String, Object>> sendFuture = new CompletableFuture<>();
    when(template.send(any(ProducerRecord.class))).thenReturn(sendFuture);
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    CompletableFuture<Void> result = publisher.publishAsync("orders", event);

    assertThat(result).isNotCompleted();
    sendFuture.complete(mock(SendResult.class));
    assertThat(result).isCompletedWithValue(null);
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishAsyncFailsWhenSendFails() {
    RuntimeException cause = new RuntimeException("broker down");
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(cause));
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    CompletableFuture<Void> result = publisher.publishAsync("orders", event);

    assertThat(result).isCompletedExceptionally();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishAsyncReturnsFailedFutureWhenSendThrowsSynchronously() {
    when(template.send(any(ProducerRecord.class))).thenThrow(new RuntimeException("boom"));
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    CompletableFuture<Void> result = publisher.publishAsync("orders", event);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).cause().isInstanceOf(DatahubException.class);
  }

  private static String headerValue(ProducerRecord<String, Object> record, String name) {
    Header header = record.headers().lastHeader(name);
    assertThat(header).as("header %s", name).isNotNull();
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
