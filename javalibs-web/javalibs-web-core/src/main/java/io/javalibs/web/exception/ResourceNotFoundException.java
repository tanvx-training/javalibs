package io.javalibs.web.exception;

import io.javalibs.web.CommonErrorCode;

/**
 * Thrown when a requested resource does not exist. Maps to HTTP 404 via
 * {@link CommonErrorCode#RESOURCE_NOT_FOUND}.
 */
public class ResourceNotFoundException extends ApiException {

    /**
     * Creates a new exception with a custom message.
     *
     * @param message human readable error message
     */
    public ResourceNotFoundException(String message) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * Convenience constructor producing a message of the form
     * {@code "Order with id 42 not found"}.
     *
     * @param resource name of the resource type, e.g. {@code "Order"}
     * @param id       identifier that could not be found
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, "%s with id %s not found".formatted(resource, id));
    }
}
