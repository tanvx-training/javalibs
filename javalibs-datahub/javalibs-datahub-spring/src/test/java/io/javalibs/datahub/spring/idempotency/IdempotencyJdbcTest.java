package io.javalibs.datahub.spring.idempotency;

import io.javalibs.datahub.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IdempotencyJdbcTest {

  private JdbcTemplate jdbc;
  private JdbcProcessedEventStore store;

  @BeforeEach
  void setUp() {
    var dataSource = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
        CREATE TABLE datahub_processed_event (
            handler      VARCHAR(255) NOT NULL,
            event_id     VARCHAR(64)  NOT NULL,
            processed_at TIMESTAMP    NOT NULL,
            PRIMARY KEY (handler, event_id)
        )""");
    store = new JdbcProcessedEventStore(jdbc, "datahub_processed_event");
  }

  @Test
  void firstMarkWinsDuplicatesReturnFalse() {
    assertThat(store.markProcessed("OrderListener", "evt-1")).isTrue();
    assertThat(store.markProcessed("OrderListener", "evt-1")).isFalse();
    // Same event for another handler is not a duplicate.
    assertThat(store.markProcessed("BillingListener", "evt-1")).isTrue();
  }

  @Test
  void processorRunsActionOncePerEvent() {
    var processor = new IdempotentEventProcessor(store);
    var envelope = EventEnvelope.of("OrderPlaced", "svc", Map.of("k", "v"));
    AtomicInteger executions = new AtomicInteger();

    assertThat(processor.process("OrderListener", envelope, executions::incrementAndGet)).isTrue();
    assertThat(processor.process("OrderListener", envelope, executions::incrementAndGet)).isFalse();
    assertThat(executions).hasValue(1);
  }

  @Test
  void deleteOlderThanRemovesOldMarkers() {
    store.markProcessed("OrderListener", "evt-old");
    jdbc.update("UPDATE datahub_processed_event SET processed_at = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
    store.markProcessed("OrderListener", "evt-new");

    assertThat(store.deleteOlderThan(Instant.now().minusSeconds(60))).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM datahub_processed_event", Integer.class)).isEqualTo(1);
  }

  @Test
  void rejectsInvalidTableName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JdbcProcessedEventStore(jdbc, "t; DROP TABLE x"));
  }
}
