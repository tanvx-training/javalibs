package io.javalibs.datahub.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the javalibs datahub module, bound to the
 * {@code javalibs.datahub.*} namespace.
 */
@ConfigurationProperties("javalibs.datahub")
public class DatahubProperties {

  /**
   * Logical name of this service used as the {@code source} of emitted events. When
   * unset, applications should fall back to {@code spring.application.name} at
   * runtime.
   */
  private String source;

  private final Kafka kafka = new Kafka();

  private final Rest rest = new Rest();

  private final Outbox outbox = new Outbox();

  private final Idempotency idempotency = new Idempotency();

  /**
   * Returns the logical event source name, or {@code null} when the application
   * should fall back to {@code spring.application.name}.
   *
   * @return the configured source name, possibly {@code null}
   */
  public String getSource() {
    return source;
  }

  /**
   * Sets the logical event source name.
   *
   * @param source the source name
   */
  public void setSource(String source) {
    this.source = source;
  }

  /**
   * Returns the Kafka publisher settings.
   *
   * @return the Kafka settings, never {@code null}
   */
  public Kafka getKafka() {
    return kafka;
  }

  /**
   * Returns the REST client settings.
   *
   * @return the REST settings, never {@code null}
   */
  public Rest getRest() {
    return rest;
  }

  /**
   * Returns the Transactional Outbox settings.
   *
   * @return the outbox settings, never {@code null}
   */
  public Outbox getOutbox() {
    return outbox;
  }

  /**
   * Returns the consumer idempotency settings.
   *
   * @return the idempotency settings, never {@code null}
   */
  public Idempotency getIdempotency() {
    return idempotency;
  }

  /**
   * Settings for the Kafka-backed {@code EventPublisher}
   * ({@code javalibs.datahub.kafka.*}).
   */
  public static class Kafka {

    /** Whether the Kafka event publisher auto-configuration is enabled. */
    private boolean enabled = true;

    /** Maximum time the blocking publish call waits for the broker acknowledgment. */
    private Duration sendTimeout = Duration.ofSeconds(30);

    /**
     * Returns whether the Kafka event publisher auto-configuration is enabled.
     *
     * @return {@code true} when enabled (the default)
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Enables or disables the Kafka event publisher auto-configuration.
     *
     * @param enabled the flag value
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns the blocking publish timeout.
     *
     * @return the send timeout, 30 seconds by default
     */
    public Duration getSendTimeout() {
      return sendTimeout;
    }

    /**
     * Sets the blocking publish timeout.
     *
     * @param sendTimeout the send timeout
     */
    public void setSendTimeout(Duration sendTimeout) {
      this.sendTimeout = sendTimeout;
    }
  }

  /**
   * Settings for the pre-configured, retrying {@code RestClient.Builder}
   * ({@code javalibs.datahub.rest.*}).
   */
  public static class Rest {

    /** Whether the datahub RestClient.Builder auto-configuration is enabled. */
    private boolean enabled = true;

    /** Maximum time to establish a TCP connection. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Maximum time to wait for response data. */
    private Duration readTimeout = Duration.ofSeconds(10);

    /** Total number of attempts (including the first) for retryable requests. */
    private int maxRetries = 3;

    /** Delay before the first retry; doubled after each failed attempt. */
    private Duration initialBackoff = Duration.ofMillis(200);

    /**
     * Returns whether the datahub RestClient.Builder auto-configuration is enabled.
     *
     * @return {@code true} when enabled (the default)
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Enables or disables the datahub RestClient.Builder auto-configuration.
     *
     * @param enabled the flag value
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns the connect timeout.
     *
     * @return the connect timeout, 5 seconds by default
     */
    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    /**
     * Sets the connect timeout.
     *
     * @param connectTimeout the connect timeout
     */
    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    /**
     * Returns the read timeout.
     *
     * @return the read timeout, 10 seconds by default
     */
    public Duration getReadTimeout() {
      return readTimeout;
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout the read timeout
     */
    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }

    /**
     * Returns the total number of attempts for retryable requests.
     *
     * @return the attempt count, 3 by default
     */
    public int getMaxRetries() {
      return maxRetries;
    }

    /**
     * Sets the total number of attempts for retryable requests.
     *
     * @param maxRetries the attempt count
     */
    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }

    /**
     * Returns the initial retry backoff.
     *
     * @return the initial backoff, 200 milliseconds by default
     */
    public Duration getInitialBackoff() {
      return initialBackoff;
    }

    /**
     * Sets the initial retry backoff.
     *
     * @param initialBackoff the initial backoff
     */
    public void setInitialBackoff(Duration initialBackoff) {
      this.initialBackoff = initialBackoff;
    }
  }

  /**
   * Settings for the Transactional Outbox ({@code javalibs.datahub.outbox.*}).
   * Opt-in: requires the outbox table (see the reference schema shipped at
   * {@code META-INF/datahub/outbox-schema-postgres.sql}).
   */
  public static class Outbox {

    /** Whether the outbox auto-configuration is enabled (opt-in). */
    private boolean enabled = false;

    /** Outbox table name. */
    private String table = "datahub_outbox_event";

    /** Maximum rows claimed per relay pass. */
    private int batchSize = 100;

    /** Publish attempts before a row is parked as FAILED. */
    private int maxAttempts = 10;

    /** Delay between relay passes. */
    private Duration pollInterval = Duration.ofSeconds(5);

    /** Append SKIP LOCKED to the claiming query (PostgreSQL/MySQL 8+/Oracle). */
    private boolean useSkipLocked = true;

    /** Whether the in-process polling relay runs (disable when using Debezium/CDC). */
    private boolean relayEnabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getTable() {
      return table;
    }

    public void setTable(String table) {
      this.table = table;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getPollInterval() {
      return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
      this.pollInterval = pollInterval;
    }

    public boolean isUseSkipLocked() {
      return useSkipLocked;
    }

    public void setUseSkipLocked(boolean useSkipLocked) {
      this.useSkipLocked = useSkipLocked;
    }

    public boolean isRelayEnabled() {
      return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
      this.relayEnabled = relayEnabled;
    }
  }

  /**
   * Settings for consumer idempotency support
   * ({@code javalibs.datahub.idempotency.*}). Opt-in: requires the
   * processed-event table from the reference schema.
   */
  public static class Idempotency {

    /** Whether the idempotency auto-configuration is enabled (opt-in). */
    private boolean enabled = false;

    /** Deduplication table name. */
    private String table = "datahub_processed_event";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getTable() {
      return table;
    }

    public void setTable(String table) {
      this.table = table;
    }
  }
}
