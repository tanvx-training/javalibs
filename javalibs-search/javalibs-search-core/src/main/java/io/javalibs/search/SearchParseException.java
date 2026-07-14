package io.javalibs.search;

/**
 * Thrown when raw search input (filter, sort, paging or combinator parameters) cannot be
 * parsed into a valid {@link SearchQuery}, or when a value cannot be converted to the
 * type required by the target field.
 *
 * <p>The message always describes precisely what was wrong with the input so it can be
 * surfaced to API clients, e.g. as an HTTP 400 response body.</p>
 */
public class SearchParseException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message a human-readable description of the parse failure
     */
    public SearchParseException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message a human-readable description of the parse failure
     * @param cause   the underlying exception (e.g. a {@code NumberFormatException})
     */
    public SearchParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
