package io.javalibs.internal.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StringsTest {

    @Test
    void blankChecks() {
        assertThat(Strings.isBlank(null)).isTrue();
        assertThat(Strings.isBlank("  ")).isTrue();
        assertThat(Strings.isBlank("x")).isFalse();
        assertThat(Strings.isNotBlank("x")).isTrue();
        assertThat(Strings.defaultIfBlank(" ", "fallback")).isEqualTo("fallback");
        assertThat(Strings.defaultIfBlank("value", "fallback")).isEqualTo("value");
    }

    @Test
    void truncate() {
        assertThat(Strings.truncate(null, 5)).isNull();
        assertThat(Strings.truncate("abc", 5)).isEqualTo("abc");
        assertThat(Strings.truncate("abcdef", 3)).isEqualTo("abc");
        assertThatIllegalArgumentException().isThrownBy(() -> Strings.truncate("abc", -1));
    }

    @Test
    void caseConversions() {
        assertThat(Strings.toSnakeCase("createdAt")).isEqualTo("created_at");
        assertThat(Strings.toSnakeCase("HTTPStatus")).isEqualTo("h_t_t_p_status");
        assertThat(Strings.toCamelCase("created_at")).isEqualTo("createdAt");
        assertThat(Strings.toCamelCase("created-at")).isEqualTo("createdAt");
        assertThat(Strings.toCamelCase("_leading")).isEqualTo("leading");
    }

    @Test
    void masking() {
        assertThat(Strings.mask("0912345678", 2)).isEqualTo("09******78");
        assertThat(Strings.mask("abc", 2)).isEqualTo("***");
        assertThat(Strings.mask(null, 2)).isNull();
        assertThat(Strings.maskEmail("john.doe@acme.com")).isEqualTo("j*******@acme.com");
        assertThat(Strings.maskEmail("not-an-email")).isEqualTo("************");
    }
}
