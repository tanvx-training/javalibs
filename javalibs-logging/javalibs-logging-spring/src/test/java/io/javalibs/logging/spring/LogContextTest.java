package io.javalibs.logging.spring;

import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void publishesTagsIntoTheMdcAndRemovesThemOnClose() {
        try (LogContext.Scope scope = LogContext.tags("auth", "login")) {
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
        }

        assertThat(MDC.get(LogFields.MDC_TAGS)).isNull();
    }

    @Test
    void nestedScopesRestoreTheOuterValueRatherThanClearingIt() {
        try (LogContext.Scope outer = LogContext.tags("auth")) {
            try (LogContext.Scope inner = LogContext.tags("login")) {
                assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
            }
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth");
        }

        assertThat(MDC.get(LogFields.MDC_TAGS)).isNull();
    }

    @Test
    void ignoresNullAndBlankTagsAndDeduplicates() {
        try (LogContext.Scope scope = LogContext.tags("auth", null, "  ", "auth", " login ")) {
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
        }
    }

    @Test
    void putStoresAndRestoresAnArbitraryKey() {
        MDC.put("orderId", "ord-1");

        try (LogContext.Scope scope = LogContext.put("orderId", "ord-2")) {
            assertThat(MDC.get("orderId")).isEqualTo("ord-2");
        }

        assertThat(MDC.get("orderId")).isEqualTo("ord-1");
    }

    @Test
    void putWithNullValueRemovesTheKeyForTheDurationOfTheScope() {
        MDC.put("orderId", "ord-1");

        try (LogContext.Scope scope = LogContext.put("orderId", null)) {
            assertThat(MDC.get("orderId")).isNull();
        }

        assertThat(MDC.get("orderId")).isEqualTo("ord-1");
    }
}
