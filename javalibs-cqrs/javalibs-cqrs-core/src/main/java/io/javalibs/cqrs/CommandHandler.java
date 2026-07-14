package io.javalibs.cqrs;

/**
 * Handles a single {@link Command} type.
 *
 * <p>Implementations should be stateless and are usually registered as beans in a
 * dependency injection container so that a {@link CommandBus} can discover them.
 * Exactly one handler must exist per command type.
 *
 * @param <C> the command type handled by this handler
 * @param <R> the result type produced by handling the command
 */
@FunctionalInterface
public interface CommandHandler<C extends Command<R>, R> {

    /**
     * Handles the given command and returns its result.
     *
     * @param command the command to handle; never {@code null}
     * @return the result of handling the command; may be {@code null} for {@link Void} results
     */
    R handle(C command);
}
