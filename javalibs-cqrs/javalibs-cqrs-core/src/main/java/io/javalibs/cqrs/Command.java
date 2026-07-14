package io.javalibs.cqrs;

/**
 * Marker interface for a command in the CQRS pattern.
 *
 * <p>A command expresses the intent to change the state of the system. Each command
 * type is handled by exactly one {@link CommandHandler}. Commands are typically
 * implemented as immutable records.
 *
 * @param <R> the type of the result produced by handling this command;
 *            use {@link Void} when the command yields no result
 */
public interface Command<R> {
}
