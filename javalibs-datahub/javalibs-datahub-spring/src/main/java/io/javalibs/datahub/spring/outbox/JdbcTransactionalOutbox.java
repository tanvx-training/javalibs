package io.javalibs.datahub.spring.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.datahub.DatahubException;
import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.TransactionalOutbox;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC-backed {@link TransactionalOutbox}.
 *
 * <p>Inserts the event into the outbox table through the caller's active
 * transaction (the {@link JdbcOperations} participates in Spring-managed
 * transactions on the same {@code DataSource}, including those driven by a
 * {@code JpaTransactionManager}). Rows are written with status {@code PENDING}
 * and picked up by {@link JdbcOutboxRelay} after commit.
 *
 * <p>The table DDL ships as a reference script at
 * {@code META-INF/datahub/outbox-schema-postgres.sql} — copy it into a Flyway
 * migration; the library never executes DDL itself.
 */
public class JdbcTransactionalOutbox implements TransactionalOutbox {

  private final JdbcOperations jdbc;
  private final ObjectMapper objectMapper;
  private final String insertSql;

  public JdbcTransactionalOutbox(JdbcOperations jdbc, ObjectMapper objectMapper, String tableName) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.insertSql = "INSERT INTO " + OutboxTables.requireValidTableName(tableName)
        + " (id, topic, event_type, source, correlation_id, partition_key,"
        + " headers_json, payload_json, payload_type, status, attempts, created_at)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)";
  }

  @Override
  public void enqueue(String topic, EventEnvelope<?> event) {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("topic must not be null or blank");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TransactionalOutbox.enqueue must run inside the database transaction that persists "
              + "your business state (annotate the calling service method with @Transactional); "
              + "otherwise the outbox pattern gives no atomicity guarantee.");
    }
    Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
    jdbc.update(insertSql,
        event.eventId(),
        topic,
        event.eventType(),
        event.source(),
        event.correlationId(),
        event.partitionKey(),
        toJson(event.headers()),
        toJson(event.payload()),
        event.payload().getClass().getName(),
        Timestamp.from(occurredAt));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new DatahubException("Failed to serialize outbox event to JSON", e);
    }
  }
}
