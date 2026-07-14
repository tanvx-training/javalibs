package io.javalibs.datahub.spring.idempotency;

import io.javalibs.datahub.ProcessedEventStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * JDBC-backed {@link ProcessedEventStore} relying on the table's primary key
 * {@code (handler, event_id)} for atomic duplicate detection: the first insert
 * wins, concurrent or repeated inserts fail with a duplicate-key violation.
 *
 * <p>Table DDL ships in {@code META-INF/datahub/outbox-schema-postgres.sql}.
 */
public class JdbcProcessedEventStore implements ProcessedEventStore {

  private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");

  private final JdbcOperations jdbc;
  private final String insertSql;
  private final String deleteSql;

  public JdbcProcessedEventStore(JdbcOperations jdbc, String tableName) {
    if (tableName == null || !IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException(
          "Invalid table name: '" + tableName + "' (letters, digits, '_' and '.' only)");
    }
    this.jdbc = jdbc;
    this.insertSql =
        "INSERT INTO " + tableName + " (handler, event_id, processed_at) VALUES (?, ?, ?)";
    this.deleteSql = "DELETE FROM " + tableName + " WHERE processed_at < ?";
  }

  @Override
  public boolean markProcessed(String handlerName, String eventId) {
    try {
      jdbc.update(insertSql, handlerName, eventId, Timestamp.from(Instant.now()));
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }

  @Override
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update(deleteSql, Timestamp.from(cutoff));
  }
}
