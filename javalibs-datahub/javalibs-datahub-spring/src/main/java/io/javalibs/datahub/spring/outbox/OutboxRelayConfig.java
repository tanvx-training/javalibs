package io.javalibs.datahub.spring.outbox;

/**
 * Tuning values for {@link JdbcOutboxRelay}.
 *
 * @param tableName     outbox table name
 * @param batchSize     maximum rows claimed per relay pass
 * @param maxAttempts   publish attempts before a row is parked as {@code FAILED}
 * @param useSkipLocked append {@code SKIP LOCKED} to the claiming query so
 *                      multiple service instances can relay concurrently
 *                      (PostgreSQL, MySQL 8+, Oracle; disable for H2)
 */
public record OutboxRelayConfig(String tableName, int batchSize, int maxAttempts, boolean useSkipLocked) {

  public OutboxRelayConfig {
    OutboxTables.requireValidTableName(tableName);
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive, got " + maxAttempts);
    }
  }
}
