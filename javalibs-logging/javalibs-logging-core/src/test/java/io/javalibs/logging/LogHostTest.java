package io.javalibs.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogHostTest {

    @Test
    void resolvesANonBlankHostNameAndCachesIt() {
        String first = LogHost.current();

        assertThat(first).isNotBlank();
        assertThat(LogHost.current()).isSameAs(first);
    }

    @Test
    void resolveNeverThrowsAndAlwaysReturnsANonBlankValue() {
        assertThatCode(LogHost::resolve).doesNotThrowAnyException();
        assertThat(LogHost.resolve()).isNotBlank();
    }
}
