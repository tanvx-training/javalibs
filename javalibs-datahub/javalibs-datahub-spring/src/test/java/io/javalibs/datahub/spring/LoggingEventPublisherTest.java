package io.javalibs.datahub.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.javalibs.datahub.EventEnvelope;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for {@link LoggingEventPublisher}.
 */
class LoggingEventPublisherTest {

  private final LoggingEventPublisher publisher = new LoggingEventPublisher();

  @Test
  void publishDoesNotThrow() {
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    assertThatCode(() -> publisher.publish("orders", event)).doesNotThrowAnyException();
  }

  @Test
  void publishAsyncCompletesSuccessfully() {
    EventEnvelope<String> event = EventEnvelope.of("order.created", "svc", "data");

    assertThat(publisher.publishAsync("orders", event)).isCompletedWithValue(null);
  }
}
