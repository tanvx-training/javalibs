package io.javalibs.cqrs;

/**
 * Handles a single {@link Query} type.
 *
 * <p>Implementations should be stateless and are usually registered as beans in a
 * dependency injection container so that a {@link QueryBus} can discover them.
 * Exactly one handler must exist per query type.
 *
 * @param <Q> the query type handled by this handler
 * @param <R> the result type returned when the query is answered
 */
@FunctionalInterface
public interface QueryHandler<Q extends Query<R>, R> {

    /**
     * Handles the given query and returns its result.
     *
     * @param query the query to handle; never {@code null}
     * @return the result of the query
     */
    R handle(Q query);
}
