package io.javalibs.datahub;

/**
 * Runtime exception thrown by datahub components when publishing or handling an event
 * fails (transport errors, timeouts, serialization problems, ...).
 */
public class DatahubException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message the detail message
   */
  public DatahubException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message the detail message
   * @param cause   the underlying cause
   */
  public DatahubException(String message, Throwable cause) {
    super(message, cause);
  }
}
