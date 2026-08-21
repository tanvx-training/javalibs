package io.javalibs.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogHostTest {

    @Test
    void resolvesANonBlankHostNameAndCachesIt() {
        String first = LogHost.current();

        assertThat(first).isNotBlank();
        assertThat(LogHost.current()).isSameAs(first);
    }
}
