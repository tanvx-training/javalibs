package io.javalibs.web.spring;

import java.util.List;

import io.javalibs.web.CommonErrorCode;
import io.javalibs.web.ErrorCode;
import io.javalibs.web.ErrorResponse;
import io.javalibs.web.ErrorResponse.FieldViolation;
import io.javalibs.web.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler translating exceptions into the standard javalibs
 * {@link ErrorResponse} contract.
 *
 * <p>This class is intentionally not annotated with a stereotype that would make it
 * eligible for component scanning; it is registered as a bean by
 * {@code javalibs-web-spring-boot-autoconfigure} (or manually by the application).</p>
 *
 * <p>The trace identifier is resolved from the SLF4J MDC using the {@code traceId} key,
 * falling back to {@code correlationId}; it is {@code null} when neither is present.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    /**
     * Handles {@link ApiException} and subclasses, using the HTTP status and code
     * carried by the exception's {@link ErrorCode}.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return the standardized error response
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("API error [{}] on {} {}: {}", errorCode.code(), request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return build(errorCode, ex.getMessage(), request, List.of());
    }

    /**
     * Handles bean validation failures on {@code @Valid} bodies and binding failures
     * ({@code MethodArgumentNotValidException} extends {@code BindException}).
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 400 response with per-field violations
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(CommonErrorCode.VALIDATION_FAILED, "Validation failed", request, violations);
    }

    /**
     * Handles constraint violations raised outside of body binding, e.g. on validated
     * method parameters.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 400 response with the violations mapped to field errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(String.valueOf(violation.getPropertyPath()),
                        violation.getMessage()))
                .toList();
        return build(CommonErrorCode.VALIDATION_FAILED, "Validation failed", request, violations);
    }

    /**
     * Handles unreadable (malformed) request bodies.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 400 response with a generic "Malformed request body" message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return build(CommonErrorCode.BAD_REQUEST, "Malformed request body", request, List.of());
    }

    /**
     * Handles type mismatches on path variables and request parameters.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 400 response describing the offending parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String message = "Parameter '%s' has invalid value '%s'".formatted(ex.getName(), ex.getValue());
        return build(CommonErrorCode.BAD_REQUEST, message, request, List.of());
    }

    /**
     * Handles missing static resources / unmapped paths (Spring 6.1+).
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 404 response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex,
            HttpServletRequest request) {
        return build(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request, List.of());
    }

    /**
     * Handles requests using an HTTP method that the target endpoint does not support.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 405 response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String message = "Request method '%s' is not supported".formatted(ex.getMethod());
        return build(CommonErrorCode.METHOD_NOT_ALLOWED, message, request, List.of());
    }

    /**
     * Catch-all handler for unexpected errors. Logs the full stack trace at ERROR level
     * and returns a generic message so that internal details are never leaked to clients.
     *
     * @param ex      the thrown exception
     * @param request the current request
     * @return a 500 response with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(CommonErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request,
            List<FieldViolation> fieldErrors) {
        ErrorResponse body = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .traceId(resolveTraceId())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }

    private static String resolveTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : MDC.get(MDC_CORRELATION_ID);
    }
}
