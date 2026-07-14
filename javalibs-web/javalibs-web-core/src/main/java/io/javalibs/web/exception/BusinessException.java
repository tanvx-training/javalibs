package io.javalibs.web.exception;

import io.javalibs.web.CommonErrorCode;
import io.javalibs.web.ErrorCode;

/**
 * Thrown when a business rule is violated. Defaults to HTTP 400 via
 * {@link CommonErrorCode#BAD_REQUEST} but can carry any {@link ErrorCode}.
 */
public class BusinessException extends ApiException {

    /**
     * Creates a new business exception mapped to {@link CommonErrorCode#BAD_REQUEST}.
     *
     * @param message human readable error message
     */
    public BusinessException(String message) {
        super(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * Creates a new business exception mapped to {@link CommonErrorCode#BAD_REQUEST}
     * with a cause.
     *
     * @param message human readable error message
     * @param cause   underlying cause
     */
    public BusinessException(String message, Throwable cause) {
        super(CommonErrorCode.BAD_REQUEST, message, cause);
    }

    /**
     * Creates a new business exception with an explicit error code.
     *
     * @param errorCode the error code describing the failure
     * @param message   human readable error message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
