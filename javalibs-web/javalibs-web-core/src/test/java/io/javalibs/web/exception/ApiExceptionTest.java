package io.javalibs.web.exception;

import io.javalibs.web.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the standard API exception hierarchy.
 */
class ApiExceptionTest {

    @Test
    void resourceNotFoundBuildsHumanReadableMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order", 42);

        assertThat(ex.getMessage()).isEqualTo("Order with id 42 not found");
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getErrorCode().httpStatus()).isEqualTo(404);
    }

    @Test
    void businessExceptionDefaultsToBadRequest() {
        BusinessException ex = new BusinessException("rule violated");

        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(ex.getErrorCode().httpStatus()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("rule violated");
    }

    @Test
    void conflictExceptionMapsTo409() {
        ConflictException ex = new ConflictException("duplicate");

        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
        assertThat(ex.getErrorCode().httpStatus()).isEqualTo(409);
    }

    @Test
    void apiExceptionCarriesErrorCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("root");
        ApiException ex = new ApiException(CommonErrorCode.SERVICE_UNAVAILABLE, "down", cause);

        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
