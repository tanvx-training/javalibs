package io.javalibs.datahub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EventEnvelope}.
 */
class EventEnvelopeTest {

  @Nested
  class OfFactory {

    @Test
    void generatesUuidEventIdAndCurrentTimestamp() {
      Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

      EventEnvelope<String> envelope = EventEnvelope.of("order.created", "order-service", "data");

      Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);
      assertThat(envelope.eventId()).isNotBlank();
      assertThatCode(() -> UUID.fromString(envelope.eventId())).doesNotThrowAnyException();
      assertThat(envelope.occurredAt()).isBetween(before, after);
      assertThat(envelope.eventType()).isEqualTo("order.created");
      assertThat(envelope.source()).isEqualTo("order-service");
      assertThat(envelope.payload()).isEqualTo("data");
      assertThat(envelope.correlationId()).isNull();
      assertThat(envelope.partitionKey()).isNull();
      assertThat(envelope.headers()).isEmpty();
    }

    @Test
    void generatesDistinctEventIds() {
      EventEnvelope<String> first = EventEnvelope.of("order.created", "svc", "a");
      EventEnvelope<String> second = EventEnvelope.of("order.created", "svc", "b");

      assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
  }

  @Nested
  class BuilderTests {

    @Test
    void roundTripsEveryField() {
      Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

      EventEnvelope<String> envelope = EventEnvelope.<String>builder()
          .eventId("evt-1")
          .eventType("order.created")
          .source("order-service")
          .occurredAt(occurredAt)
          .correlationId("corr-1")
          .partitionKey("order-42")
          .header("tenant", "acme")
          .headers(Map.of("region", "eu"))
          .payload("data")
          .build();

      assertThat(envelope.eventId()).isEqualTo("evt-1");
      assertThat(envelope.eventType()).isEqualTo("order.created");
      assertThat(envelope.source()).isEqualTo("order-service");
      assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
      assertThat(envelope.correlationId()).isEqualTo("corr-1");
      assertThat(envelope.partitionKey()).isEqualTo("order-42");
      assertThat(envelope.headers())
          .containsEntry("tenant", "acme")
          .containsEntry("region", "eu")
          .hasSize(2);
      assertThat(envelope.payload()).isEqualTo("data");
    }

    @Test
    void defaultsEventIdAndOccurredAtWhenUnset() {
      EventEnvelope<String> envelope = EventEnvelope.<String>builder()
          .eventType("order.created")
          .payload("data")
          .build();

      assertThat(envelope.eventId()).isNotBlank();
      assertThatCode(() -> UUID.fromString(envelope.eventId())).doesNotThrowAnyException();
      assertThat(envelope.occurredAt()).isNotNull();
    }
  }

  @Nested
  class Validation {

    @Test
    void rejectsNullEventType() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new EventEnvelope<>(
              "id", null, "svc", Instant.now(), null, null, Map.of(), "data"))
          .withMessageContaining("eventType");
    }

    @Test
    void rejectsBlankEventType() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new EventEnvelope<>(
              "id", "  ", "svc", Instant.now(), null, null, Map.of(), "data"))
          .withMessageContaining("eventType");
    }

    @Test
    void rejectsNullPayload() {
      assertThatNullPointerException()
          .isThrownBy(() -> new EventEnvelope<>(
              "id", "order.created", "svc", Instant.now(), null, null, Map.of(), null))
          .withMessageContaining("payload");
    }

    @Test
    void builderAppliesSameValidation() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventEnvelope.builder().payload("data").build());
      assertThatNullPointerException()
          .isThrownBy(() -> EventEnvelope.builder().eventType("order.created").build());
    }
  }

  @Nested
  class HeaderSafety {

    @Test
    void copiesHeadersDefensively() {
      Map<String, String> mutable = new HashMap<>();
      mutable.put("tenant", "acme");

      EventEnvelope<String> envelope = new EventEnvelope<>(
          "id", "order.created", "svc", Instant.now(), null, null, mutable, "data");
      mutable.put("tenant", "changed");
      mutable.put("extra", "value");

      assertThat(envelope.headers()).containsExactlyEntriesOf(Map.of("tenant", "acme"));
    }

    @Test
    void headersAreImmutable() {
      EventEnvelope<String> envelope = EventEnvelope.of("order.created", "svc", "data");

      assertThatThrownBy(() -> envelope.headers().put("k", "v"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullHeadersBecomeEmptyMap() {
      EventEnvelope<String> envelope = new EventEnvelope<>(
          "id", "order.created", "svc", Instant.now(), null, null, null, "data");

      assertThat(envelope.headers()).isNotNull().isEmpty();
    }
  }
}
