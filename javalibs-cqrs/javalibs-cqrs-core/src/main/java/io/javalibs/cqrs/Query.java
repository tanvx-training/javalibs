package io.javalibs.cqrs;

/**
 * Marker interface for a query in the CQRS pattern.
 *
 * <p>A query expresses the intent to read data without changing the state of the
 * system. Each query type is handled by exactly one {@link QueryHandler}. Queries
 * are typically implemented as immutable records.
 *
 * @param <R> the type of the result returned when this query is answered
 */
public interface Query<R> {
}
