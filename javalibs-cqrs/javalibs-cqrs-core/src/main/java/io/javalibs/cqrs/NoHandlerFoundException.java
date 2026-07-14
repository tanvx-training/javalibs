package io.javalibs.cqrs;

/**
 * Thrown when a {@link CommandBus} or {@link QueryBus} receives a message
 * (command or query) for which no handler has been registered.
 */
public class NoHandlerFoundException extends CqrsException {

    /** The concrete command or query class that had no handler. */
    private final Class<?> messageType;

    /**
     * Creates a new exception for the given unhandled message type.
     *
     * @param messageType the command or query class for which no handler was found
     */
    public NoHandlerFoundException(Class<?> messageType) {
        super("No handler registered for [" + messageType.getName() + "]");
        this.messageType = messageType;
    }

    /**
     * Returns the command or query class for which no handler was found.
     *
     * @return the unhandled message type
     */
    public Class<?> getMessageType() {
        return messageType;
    }
}
