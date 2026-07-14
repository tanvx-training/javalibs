package io.javalibs.datahub.spring.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives {@link JdbcOutboxRelay} on a fixed delay using a dedicated daemon
 * thread. Implemented as a {@link SmartLifecycle} so it starts after the
 * application context is ready and stops gracefully on shutdown — no
 * {@code @EnableScheduling} required in the application.
 */
public class OutboxRelayScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

  private final JdbcOutboxRelay relay;
  private final Duration pollInterval;
  private ScheduledExecutorService executor;
  private volatile boolean running;

  public OutboxRelayScheduler(JdbcOutboxRelay relay, Duration pollInterval) {
    if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
      throw new IllegalArgumentException("pollInterval must be positive, got " + pollInterval);
    }
    this.relay = relay;
    this.pollInterval = pollInterval;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "datahub-outbox-relay");
      thread.setDaemon(true);
      return thread;
    });
    long delayMillis = pollInterval.toMillis();
    executor.scheduleWithFixedDelay(this::relaySafely, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    running = true;
    log.info("Outbox relay started (poll interval {})", pollInterval);
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
      executor = null;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void relaySafely() {
    try {
      int published = relay.relayBatch();
      if (published > 0) {
        log.debug("Outbox relay published {} event(s)", published);
      }
    } catch (Exception e) {
      log.warn("Outbox relay pass failed; will retry on next tick", e);
    }
  }
}
