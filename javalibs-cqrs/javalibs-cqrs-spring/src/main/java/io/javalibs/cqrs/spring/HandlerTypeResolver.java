package io.javalibs.cqrs.spring;

import io.javalibs.cqrs.CqrsException;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * Internal helper that resolves the message type (command or query class)
 * handled by a given handler instance.
 *
 * <p>Handlers may be proxied (for example by {@code @Transactional}), so the
 * generic type parameter is resolved against the user class obtained via
 * {@link ClassUtils#getUserClass(Object)} rather than the runtime (proxy) class.
 */
final class HandlerTypeResolver {

    private HandlerTypeResolver() {
    }

    /**
     * Resolves the first generic type parameter of {@code handlerInterface}
     * as implemented by the given handler instance.
     *
     * @param handlerInterface the generic handler interface, e.g. {@code CommandHandler.class}
     * @param markerType       the marker interface bounding the message type,
     *                         e.g. {@code Command.class}
     * @param handler          the handler instance (possibly a proxy)
     * @return the concrete message class handled by the handler
     * @throws CqrsException if the generic type parameter cannot be resolved
     *                       (for example when the handler is a lambda)
     */
    static Class<?> resolveMessageType(Class<?> handlerInterface, Class<?> markerType, Object handler) {
        Class<?> userClass = ClassUtils.getUserClass(handler);
        Class<?> messageType = ResolvableType.forClass(handlerInterface, userClass)
                .getGeneric(0)
                .resolve();
        // When the generic cannot be resolved (typical for lambdas), ResolvableType
        // either returns null or falls back to the type-variable bound, which is the
        // bare marker interface -- never a valid concrete message class.
        if (messageType == null || messageType == markerType) {
            throw new CqrsException("Unable to resolve the message type handled by ["
                    + userClass.getName() + "]. Implement " + handlerInterface.getSimpleName()
                    + " with a concrete class (lambdas do not retain generic type information).");
        }
        return messageType;
    }
}
