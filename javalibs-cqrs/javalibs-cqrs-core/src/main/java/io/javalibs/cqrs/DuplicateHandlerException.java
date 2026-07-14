package io.javalibs.cqrs;

/**
 * Thrown when two handlers are registered for the same command or query type.
 *
 * <p>CQRS requires a one-to-one relationship between a message type and its
 * handler, so bus implementations fail fast at construction time when they
 * detect an ambiguous registration.
 */
public class DuplicateHandlerException extends CqrsException {

    /** The concrete command or query class that has more than one handler. */
    private final Class<?> messageType;

    /**
     * Creates a new exception for the given ambiguous message type.
     *
     * @param messageType     the command or query class registered twice
     * @param existingHandler the handler class already registered for the type
     * @param newHandler      the conflicting handler class
     */
    public DuplicateHandlerException(Class<?> messageType, Class<?> existingHandler, Class<?> newHandler) {
        super("Duplicate handler for [" + messageType.getName() + "]: ["
                + existingHandler.getName() + "] and [" + newHandler.getName() + "]");
        this.messageType = messageType;
    }

    /**
     * Returns the command or query class that has more than one handler.
     *
     * @return the ambiguous message type
     */
    public Class<?> getMessageType() {
        return messageType;
    }
}
