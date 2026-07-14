package io.javalibs.datahub;

import java.time.Instant;

/**
 * Deduplication store enabling idempotent event consumers.
 *
 * <p>Message brokers deliver at-least-once: the same event can reach a listener
 * twice (rebalance, retry, relay redelivery). Before executing its side effects,
 * an idempotent listener records the {@code (handlerName, eventId)} pair here;
 * when the pair already exists the event is a duplicate and must be skipped.
 *
 * <p>Call inside the listener's database transaction so the marker and the
 * business writes commit (or roll back) atomically.
 */
public interface ProcessedEventStore {

  /**
   * Atomically records that the handler processed the event.
   *
   * @param handlerName stable logical name of the consumer (e.g. the listener class)
   * @param eventId     unique id of the event occurrence
   * @return {@code true} when recorded for the first time; {@code false} when the
   *     pair already existed (duplicate delivery — skip processing)
   */
  boolean markProcessed(String handlerName, String eventId);

  /**
   * Housekeeping: removes markers older than the cutoff (duplicates arrive within
   * minutes, keeping markers forever is unnecessary).
   *
   * @param cutoff markers processed before this instant are deleted
   * @return number of removed rows
   */
  int deleteOlderThan(Instant cutoff);
}
