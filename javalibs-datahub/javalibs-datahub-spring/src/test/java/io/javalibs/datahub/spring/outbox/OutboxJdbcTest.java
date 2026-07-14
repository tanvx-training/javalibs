package io.javalibs.datahub.spring.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class OutboxJdbcTest {

  private static final String DDL = """
      CREATE TABLE datahub_outbox_event (
          id             VARCHAR(64)  PRIMARY KEY,
          topic          VARCHAR(255) NOT NULL,
          event_type     VARCHAR(255) NOT NULL,
          source         VARCHAR(255),
          correlation_id VARCHAR(128),
          partition_key  VARCHAR(255),
          headers_json   TEXT,
          payload_json   TEXT         NOT NULL,
          payload_type   VARCHAR(512),
          status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
          attempts       INT          NOT NULL DEFAULT 0,
          last_error     TEXT,
          created_at     TIMESTAMP    NOT NULL,
          published_at   TIMESTAMP
      )""";

  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private final ObjectMapper mapper = new ObjectMapper();
  private JdbcTransactionalOutbox outbox;
  private EventPublisher publisher;

  @BeforeEach
  void setUp() {
    var dataSource = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(DDL);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    outbox = new JdbcTransactionalOutbox(jdbc, mapper, "datahub_outbox_event");
    publisher = Mockito.mock(EventPublisher.class);
  }

  private JdbcOutboxRelay relay(int maxAttempts) {
    return new JdbcOutboxRelay(jdbc, tx, publisher, mapper,
        new OutboxRelayConfig("datahub_outbox_event", 100, maxAttempts, false));
  }

  private void enqueue(String topic, String eventType) {
    tx.executeWithoutResult(status -> outbox.enqueue(topic,
        EventEnvelope.of(eventType, "test-service", Map.of("orderId", "42"))));
  }

  @Test
  void enqueueOutsideTransactionThrows() {
    assertThatIllegalStateException()
        .isThrownBy(() -> outbox.enqueue("orders.events",
            EventEnvelope.of("OrderPlaced", "svc", Map.of())))
        .withMessageContaining("@Transactional");
  }

  @Test
  void enqueuePersistsPendingRow() {
    enqueue("orders.events", "OrderPlaced");

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM datahub_outbox_event");
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("topic")).isEqualTo("orders.events");
    assertThat(row.get("event_type")).isEqualTo("OrderPlaced");
    assertThat(row.get("source")).isEqualTo("test-service");
    assertThat((String) row.get("payload_json")).contains("\"orderId\":\"42\"");
    assertThat(row.get("attempts")).isEqualTo(0);
    assertThat(row.get("created_at")).isNotNull();
  }

  @Test
  void relayPublishesAndMarksPublished() {
    enqueue("orders.events", "OrderPlaced");

    int published = relay(3).relayBatch();

    assertThat(published).isEqualTo(1);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<EventEnvelope<?>> captor =
        (ArgumentCaptor<EventEnvelope<?>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(EventEnvelope.class);
    Mockito.verify(publisher).publish(eq("orders.events"), captor.capture());
    EventEnvelope<?> sent = captor.getValue();
    assertThat(sent.eventType()).isEqualTo("OrderPlaced");
    assertThat(((JsonNode) sent.payload()).get("orderId").asText()).isEqualTo("42");

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM datahub_outbox_event");
    assertThat(row.get("status")).isEqualTo("PUBLISHED");
    assertThat(row.get("published_at")).isNotNull();
  }

  @Test
  void failuresIncrementAttemptsThenParkAsFailed() {
    Mockito.doThrow(new RuntimeException("broker down"))
        .when(publisher).publish(anyString(), any());
    enqueue("orders.events", "OrderPlaced");
    JdbcOutboxRelay relay = relay(2);

    assertThat(relay.relayBatch()).isZero();
    Map<String, Object> afterFirst = jdbc.queryForMap("SELECT * FROM datahub_outbox_event");
    assertThat(afterFirst.get("status")).isEqualTo("PENDING");
    assertThat(afterFirst.get("attempts")).isEqualTo(1);
    assertThat((String) afterFirst.get("last_error")).contains("broker down");

    assertThat(relay.relayBatch()).isZero();
    Map<String, Object> afterSecond = jdbc.queryForMap("SELECT * FROM datahub_outbox_event");
    assertThat(afterSecond.get("status")).isEqualTo("FAILED");
    assertThat(afterSecond.get("attempts")).isEqualTo(2);

    // FAILED rows are no longer claimed.
    assertThat(relay.relayBatch()).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT attempts FROM datahub_outbox_event", Integer.class)).isEqualTo(2);
  }

  @Test
  void poisonRowDoesNotBlockOthers() {
    Mockito.doThrow(new RuntimeException("poison"))
        .when(publisher).publish(eq("bad.topic"), any());
    enqueue("bad.topic", "BadEvent");
    enqueue("orders.events", "OrderPlaced");

    assertThat(relay(5).relayBatch()).isEqualTo(1);

    assertThat(jdbc.queryForObject(
        "SELECT status FROM datahub_outbox_event WHERE topic = 'orders.events'", String.class))
        .isEqualTo("PUBLISHED");
    assertThat(jdbc.queryForObject(
        "SELECT status FROM datahub_outbox_event WHERE topic = 'bad.topic'", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void schedulerRunsRelayAndStops() throws Exception {
    JdbcOutboxRelay mockRelay = Mockito.mock(JdbcOutboxRelay.class);
    var scheduler = new OutboxRelayScheduler(mockRelay, java.time.Duration.ofMillis(20));

    scheduler.start();
    assertThat(scheduler.isRunning()).isTrue();
    Thread.sleep(300);
    Mockito.verify(mockRelay, Mockito.atLeastOnce()).relayBatch();

    scheduler.stop();
    assertThat(scheduler.isRunning()).isFalse();
  }

  @Test
  void schemaResourceShipsOnClasspath() {
    assertThat(getClass().getResource("/META-INF/datahub/outbox-schema-postgres.sql")).isNotNull();
  }
}
