package io.javalibs.web;

import java.util.regex.Pattern;

/**
 * Standardized business error code following the platform-wide convention
 * {@code ERR-<DOMAIN>-<SEQUENCE>}, e.g. {@code ERR-USER-001} or
 * {@code ERR-ORDER-042}.
 *
 * <p>Services define their business error catalog as constants and raise them
 * through {@link io.javalibs.web.exception.ApiException}; the code lands in the
 * {@code code} field of the standard {@code ErrorResponse} JSON, giving clients
 * a stable identifier to program against (as opposed to parsing messages):</p>
 *
 * <pre>{@code
 * public final class UserErrors {
 *     public static final BusinessErrorCode NOT_FOUND  = BusinessErrorCode.of("ERR-USER-001", 404);
 *     public static final BusinessErrorCode SUSPENDED  = BusinessErrorCode.of("ERR-USER-002", 403);
 *     public static final BusinessErrorCode DUPLICATED = BusinessErrorCode.of("ERR-USER-003", 409);
 * }
 *
 * throw new ApiException(UserErrors.SUSPENDED, "User account is suspended");
 * }</pre>
 *
 * @param code       stable identifier matching {@code ERR-<DOMAIN>-<SEQUENCE>}
 * @param httpStatus HTTP status the error is served with (4xx or 5xx)
 */
public record BusinessErrorCode(String code, int httpStatus) implements ErrorCode {

    private static final Pattern FORMAT = Pattern.compile("ERR(-[A-Z0-9]+)+");

    public BusinessErrorCode {
        if (code == null || !FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Business error code must match ERR-<DOMAIN>-<SEQUENCE> (uppercase), got: " + code);
        }
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException(
                    "httpStatus must be a 4xx or 5xx status, got: " + httpStatus);
        }
    }

    /**
     * Creates a business error code.
     *
     * @param code       stable identifier, e.g. {@code "ERR-USER-001"}
     * @param httpStatus HTTP status to serve the error with
     * @return the validated error code
     */
    public static BusinessErrorCode of(String code, int httpStatus) {
        return new BusinessErrorCode(code, httpStatus);
    }
}
