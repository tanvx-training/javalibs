package io.javalibs.web;

import io.javalibs.web.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BusinessErrorCodeTest {

    @Test
    void createsValidCodes() {
        BusinessErrorCode code = BusinessErrorCode.of("ERR-USER-001", 404);
        assertThat(code.code()).isEqualTo("ERR-USER-001");
        assertThat(code.httpStatus()).isEqualTo(404);
        assertThat(BusinessErrorCode.of("ERR-ORDER-PAYMENT-42", 409).code())
                .isEqualTo("ERR-ORDER-PAYMENT-42");
    }

    @Test
    void rejectsBadFormat() {
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of(null, 400));
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("USER-001", 400));
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("ERR-user-001", 400));
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("ERR-", 400));
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("ERR", 400));
    }

    @Test
    void rejectsNonErrorHttpStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("ERR-X-1", 200));
        assertThatIllegalArgumentException().isThrownBy(() -> BusinessErrorCode.of("ERR-X-1", 600));
    }

    @Test
    void worksWithApiException() {
        var suspended = BusinessErrorCode.of("ERR-USER-002", 403);
        var ex = new ApiException(suspended, "User account is suspended");
        assertThat(ex.getErrorCode().code()).isEqualTo("ERR-USER-002");
        assertThat(ex.getErrorCode().httpStatus()).isEqualTo(403);
    }
}
