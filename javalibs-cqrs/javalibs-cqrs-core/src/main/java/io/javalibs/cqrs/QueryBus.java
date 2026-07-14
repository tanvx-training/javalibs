package io.javalibs.cqrs;

/**
 * Central dispatcher for {@link Query queries}.
 *
 * <p>A query bus routes each query to the single {@link QueryHandler} registered
 * for its concrete type and returns the handler's result.
 */
public interface QueryBus {

    /**
     * Asks the given query and returns the answer produced by its registered handler.
     *
     * @param query the query to ask; must not be {@code null}
     * @param <R>   the result type declared by the query
     * @return the result produced by the matching handler
     * @throws NoHandlerFoundException if no handler is registered for the query type
     */
    <R> R ask(Query<R> query);
}
