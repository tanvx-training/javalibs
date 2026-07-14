package io.javalibs.cqrs.spring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.javalibs.cqrs.DuplicateHandlerException;
import io.javalibs.cqrs.NoHandlerFoundException;
import io.javalibs.cqrs.Query;
import io.javalibs.cqrs.QueryBus;
import io.javalibs.cqrs.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

/**
 * {@link QueryBus} implementation that routes queries to handlers discovered
 * from a Spring application context.
 *
 * <p>At construction time the bus inspects every supplied {@link QueryHandler}
 * and resolves the concrete {@link Query} class it handles from its generic
 * type parameter (using the user class, so proxied handlers work). Asking a
 * query is then a simple map lookup on the query's runtime class.
 *
 * <p>The bus fails fast with a {@link DuplicateHandlerException} when two
 * handlers claim the same query type, and throws
 * {@link NoHandlerFoundException} at dispatch time when no handler matches.
 */
public class SpringQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(SpringQueryBus.class);

    private final Map<Class<?>, QueryHandler<?, ?>> handlers;

    /**
     * Creates a query bus backed by the given handlers.
     *
     * @param queryHandlers the query handlers to register; must not be {@code null}
     * @throws DuplicateHandlerException if two handlers handle the same query class
     */
    public SpringQueryBus(List<QueryHandler<?, ?>> queryHandlers) {
        Objects.requireNonNull(queryHandlers, "queryHandlers must not be null");
        Map<Class<?>, QueryHandler<?, ?>> registry = new HashMap<>();
        for (QueryHandler<?, ?> handler : queryHandlers) {
            Class<?> queryType =
                    HandlerTypeResolver.resolveMessageType(QueryHandler.class, Query.class, handler);
            QueryHandler<?, ?> existing = registry.putIfAbsent(queryType, handler);
            if (existing != null) {
                throw new DuplicateHandlerException(queryType,
                        ClassUtils.getUserClass(existing), ClassUtils.getUserClass(handler));
            }
            logger.debug("Registered query handler [{}] for query [{}]",
                    ClassUtils.getUserClass(handler).getName(), queryType.getName());
        }
        this.handlers = Map.copyOf(registry);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R ask(Query<R> query) {
        Objects.requireNonNull(query, "query must not be null");
        Class<?> queryType = query.getClass();
        QueryHandler<Query<R>, R> handler =
                (QueryHandler<Query<R>, R>) handlers.get(queryType);
        if (handler == null) {
            throw new NoHandlerFoundException(queryType);
        }
        return handler.handle(query);
    }
}
