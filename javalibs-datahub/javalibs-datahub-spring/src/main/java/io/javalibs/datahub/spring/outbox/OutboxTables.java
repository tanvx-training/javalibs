package io.javalibs.datahub.spring.outbox;

import java.util.regex.Pattern;

/**
 * Shared validation for configurable table names (defense against SQL injection
 * through configuration, since table names cannot be bound as statement
 * parameters).
 */
final class OutboxTables {

  private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");

  private OutboxTables() {
  }

  static String requireValidTableName(String tableName) {
    if (tableName == null || !IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException(
          "Invalid table name: '" + tableName + "' (letters, digits, '_' and '.' only)");
    }
    return tableName;
  }
}
