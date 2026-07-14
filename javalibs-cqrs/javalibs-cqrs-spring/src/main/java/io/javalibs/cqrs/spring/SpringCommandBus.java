package io.javalibs.cqrs.spring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandBus;
import io.javalibs.cqrs.CommandHandler;
import io.javalibs.cqrs.DuplicateHandlerException;
import io.javalibs.cqrs.NoHandlerFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

/**
 * {@link CommandBus} implementation that routes commands to handlers discovered
 * from a Spring application context.
 *
 * <p>At construction time the bus inspects every supplied {@link CommandHandler}
 * and resolves the concrete {@link Command} class it handles from its generic
 * type parameter (using the user class, so proxied handlers work). Dispatching
 * is then a simple map lookup on the command's runtime class.
 *
 * <p>The bus fails fast with a {@link DuplicateHandlerException} when two
 * handlers claim the same command type, and throws
 * {@link NoHandlerFoundException} at dispatch time when no handler matches.
 */
public class SpringCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SpringCommandBus.class);

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    /**
     * Creates a command bus backed by the given handlers.
     *
     * @param commandHandlers the command handlers to register; must not be {@code null}
     * @throws DuplicateHandlerException if two handlers handle the same command class
     */
    public SpringCommandBus(List<CommandHandler<?, ?>> commandHandlers) {
        Objects.requireNonNull(commandHandlers, "commandHandlers must not be null");
        Map<Class<?>, CommandHandler<?, ?>> registry = new HashMap<>();
        for (CommandHandler<?, ?> handler : commandHandlers) {
            Class<?> commandType =
                    HandlerTypeResolver.resolveMessageType(CommandHandler.class, Command.class, handler);
            CommandHandler<?, ?> existing = registry.putIfAbsent(commandType, handler);
            if (existing != null) {
                throw new DuplicateHandlerException(commandType,
                        ClassUtils.getUserClass(existing), ClassUtils.getUserClass(handler));
            }
            logger.debug("Registered command handler [{}] for command [{}]",
                    ClassUtils.getUserClass(handler).getName(), commandType.getName());
        }
        this.handlers = Map.copyOf(registry);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Command<R> command) {
        Objects.requireNonNull(command, "command must not be null");
        Class<?> commandType = command.getClass();
        CommandHandler<Command<R>, R> handler =
                (CommandHandler<Command<R>, R>) handlers.get(commandType);
        if (handler == null) {
            throw new NoHandlerFoundException(commandType);
        }
        return handler.handle(command);
    }
}
