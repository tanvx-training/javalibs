package io.javalibs.web;

/**
 * Generic error codes shared by every javalibs based service.
 *
 * <p>Applications are encouraged to define their own {@link ErrorCode} enums for domain
 * specific failures and reserve these codes for cross-cutting, technical errors.</p>
 */
public enum CommonErrorCode implements ErrorCode {

    /** Request payload or parameters failed validation. */
    VALIDATION_FAILED("ERR_VALIDATION", 400),

    /** Request is malformed or otherwise not processable. */
    BAD_REQUEST("ERR_BAD_REQUEST", 400),

    /** Authentication is required or has failed. */
    UNAUTHORIZED("ERR_UNAUTHORIZED", 401),

    /** Caller is authenticated but not allowed to perform the operation. */
    FORBIDDEN("ERR_FORBIDDEN", 403),

    /** The requested resource does not exist. */
    RESOURCE_NOT_FOUND("ERR_RESOURCE_NOT_FOUND", 404),

    /** HTTP method is not supported for the requested resource. */
    METHOD_NOT_ALLOWED("ERR_METHOD_NOT_ALLOWED", 405),

    /** The request conflicts with the current state of the resource. */
    CONFLICT("ERR_CONFLICT", 409),

    /** Unexpected server side failure. */
    INTERNAL_ERROR("ERR_INTERNAL", 500),

    /** The service is temporarily unavailable. */
    SERVICE_UNAVAILABLE("ERR_SERVICE_UNAVAILABLE", 503);

    private final String code;
    private final int httpStatus;

    CommonErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
