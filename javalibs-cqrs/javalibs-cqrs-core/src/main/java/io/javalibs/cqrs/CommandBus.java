package io.javalibs.cqrs;

/**
 * Central dispatcher for {@link Command commands}.
 *
 * <p>A command bus routes each command to the single {@link CommandHandler}
 * registered for its concrete type and returns the handler's result.
 */
public interface CommandBus {

    /**
     * Dispatches the given command to its registered handler.
     *
     * @param command the command to dispatch; must not be {@code null}
     * @param <R>     the result type declared by the command
     * @return the result produced by the matching handler
     * @throws NoHandlerFoundException if no handler is registered for the command type
     */
    <R> R dispatch(Command<R> command);
}
