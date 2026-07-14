package io.javalibs.internal.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ChecksTest {

    @Test
    void notNullReturnsValue() {
        assertThat(Checks.notNull("v", "field")).isEqualTo("v");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Checks.notNull(null, "field"))
                .withMessage("field must not be null");
    }

    @Test
    void notBlank() {
        assertThat(Checks.notBlank("v", "field")).isEqualTo("v");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Checks.notBlank(" ", "field"))
                .withMessage("field must not be blank");
    }

    @Test
    void notEmpty() {
        assertThat(Checks.notEmpty(List.of(1), "list")).containsExactly(1);
        assertThatIllegalArgumentException().isThrownBy(() -> Checks.notEmpty(List.of(), "list"));
        assertThat(Checks.notEmpty(Map.of("k", "v"), "map")).containsEntry("k", "v");
        assertThatIllegalArgumentException().isThrownBy(() -> Checks.notEmpty(Map.of(), "map"));
    }

    @Test
    void conditions() {
        Checks.isTrue(true, "ok");
        assertThatIllegalArgumentException().isThrownBy(() -> Checks.isTrue(false, "bad arg"));
        Checks.state(true, "ok");
        assertThatIllegalStateException().isThrownBy(() -> Checks.state(false, "bad state"));
    }
}
