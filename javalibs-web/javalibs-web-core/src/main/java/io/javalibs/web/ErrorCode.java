package io.javalibs.web;

/**
 * Contract for machine-readable error codes exposed by an API.
 *
 * <p>Applications typically implement this interface with an enum of domain specific
 * error codes; {@link CommonErrorCode} provides the generic ones. An error code maps a
 * stable string identifier (safe to program against on the client side) to the HTTP
 * status it should be served with.</p>
 */
public interface ErrorCode {

    /**
     * Returns the stable, machine-readable code, e.g. {@code "ERR_VALIDATION"}.
     *
     * @return the error code string
     */
    String code();

    /**
     * Returns the HTTP status code this error should be served with, e.g. {@code 404}.
     *
     * @return the HTTP status code
     */
    int httpStatus();
}
