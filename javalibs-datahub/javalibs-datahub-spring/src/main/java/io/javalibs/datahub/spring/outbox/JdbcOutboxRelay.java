package io.javalibs.datahub.spring.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls {@code PENDING} outbox rows and publishes them through the configured
 * {@link EventPublisher}, marking them {@code PUBLISHED} on success. Failed
 * rows stay {@code PENDING} (with an incremented attempt counter and the last
 * error recorded) until {@code maxAttempts} is reached, after which they are
 * parked as {@code FAILED} for manual inspection.
 *
 * <p>Each relay pass runs in its own transaction and claims rows with
 * {@code FOR UPDATE [SKIP LOCKED]}, so multiple service instances can relay
 * concurrently without double-publishing within the lock window. Delivery is
 * still at-least-once overall — consumers must be idempotent.
 *
 * <p>For very high volume, replace this polling relay with CDC (e.g. Debezium
 * reading the outbox table) — the table layout is compatible.
 */
public class JdbcOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(JdbcOutboxRelay.class);
  private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {
  };

  private final JdbcOperations jdbc;
  private final TransactionOperations transaction;
  private final EventPublisher publisher;
  private final ObjectMapper objectMapper;
  private final OutboxRelayConfig config;
  private final String selectSql;
  private final String markPublishedSql;
  private final String markFailureSql;

  public JdbcOutboxRelay(JdbcOperations jdbc, TransactionOperations transaction,
      EventPublisher publisher, ObjectMapper objectMapper, OutboxRelayConfig config) {
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
    this.config = config;
    String table = config.tableName();
    this.selectSql = "SELECT id, topic, event_type, source, correlation_id, partition_key,"
        + " headers_json, payload_json, attempts, created_at FROM " + table
        + " WHERE status = 'PENDING' ORDER BY created_at"
        + " FETCH FIRST " + config.batchSize() + " ROWS ONLY"
        + " FOR UPDATE" + (config.useSkipLocked() ? " SKIP LOCKED" : "");
    this.markPublishedSql =
        "UPDATE " + table + " SET status = 'PUBLISHED', published_at = ? WHERE id = ?";
    this.markFailureSql =
        "UPDATE " + table + " SET status = ?, attempts = ?, last_error = ? WHERE id = ?";
  }

  /**
   * Runs one relay pass.
   *
   * @return the number of events published successfully in this pass
   */
  public int relayBatch() {
    Integer published = transaction.execute(status -> {
      List<Map<String, Object>> rows = jdbc.queryForList(selectSql);
      int success = 0;
      for (Map<String, Object> row : rows) {
        if (relayRow(row)) {
          success++;
        }
      }
      return success;
    });
    return published != null ? published : 0;
  }

  private boolean relayRow(Map<String, Object> row) {
    String id = (String) row.get("id");
    String topic = (String) row.get("topic");
    try {
      publisher.publish(topic, toEnvelope(row));
      jdbc.update(markPublishedSql, Timestamp.from(Instant.now()), id);
      return true;
    } catch (Exception e) {
      int attempts = ((Number) row.get("attempts")).intValue() + 1;
      boolean failed = attempts >= config.maxAttempts();
      String error = truncate(e.toString());
      jdbc.update(markFailureSql, failed ? "FAILED" : "PENDING", attempts, error, id);
      if (failed) {
        log.error("Outbox event {} (topic {}) parked as FAILED after {} attempts: {}",
            id, topic, attempts, error);
      } else {
        log.warn("Outbox event {} (topic {}) publish attempt {}/{} failed: {}",
            id, topic, attempts, config.maxAttempts(), error);
      }
      return false;
    }
  }

  private EventEnvelope<JsonNode> toEnvelope(Map<String, Object> row) throws Exception {
    Timestamp createdAt = (Timestamp) row.get("created_at");
    String headersJson = (String) row.get("headers_json");
    EventEnvelope.Builder<JsonNode> builder = EventEnvelope.<JsonNode>builder()
        .eventId((String) row.get("id"))
        .eventType((String) row.get("event_type"))
        .source((String) row.get("source"))
        .occurredAt(createdAt != null ? createdAt.toInstant() : null)
        .correlationId((String) row.get("correlation_id"))
        .partitionKey((String) row.get("partition_key"))
        .payload(objectMapper.readTree((String) row.get("payload_json")));
    if (headersJson != null && !headersJson.isBlank()) {
      builder.headers(objectMapper.readValue(headersJson, HEADERS_TYPE));
    }
    return builder.build();
  }

  private static String truncate(String value) {
    return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
  }
}
