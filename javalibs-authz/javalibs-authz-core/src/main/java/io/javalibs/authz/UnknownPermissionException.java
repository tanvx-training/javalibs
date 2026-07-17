package io.javalibs.authz;

/**
 * Exception thrown when a permission code is not registered in the application's
 * {@link PermissionCatalog}. This exception is raised during authorization validation
 * to detect typos or configuration errors early.
 *
 * <p>This is a runtime exception intended to be thrown when an unknown permission code
 * is encountered and validation is required, ensuring fail-fast behavior for permission
 * management operations.
 */
public class UnknownPermissionException extends RuntimeException {

    /**
     * Constructs an {@code UnknownPermissionException} with the specified detail message.
     *
     * @param message the detail message describing which permission codes are unknown
     *                and available registered codes
     */
    public UnknownPermissionException(String message) {
        super(message);
    }
}
