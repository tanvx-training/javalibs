package io.javalibs.observability;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Nested
    class Generate {

        @Test
        void returnsThirtyTwoLowerCaseHexCharacters() {
            String id = CorrelationId.generate();

            assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
        }

        @Test
        void returnsUniqueValues() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1_000; i++) {
                ids.add(CorrelationId.generate());
            }

            assertThat(ids).hasSize(1_000);
        }

        @Test
        void generatedValueIsValid() {
            assertThat(CorrelationId.isValid(CorrelationId.generate())).isTrue();
        }
    }

    @Nested
    class IsValid {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        void rejectsNullAndBlank(String value) {
            assertThat(CorrelationId.isValid(value)).isFalse();
        }

        @Test
        void rejectsCrLfInjection() {
            assertThat(CorrelationId.isValid("abc\r\ndef")).isFalse();
            assertThat(CorrelationId.isValid("abc\rdef")).isFalse();
            assertThat(CorrelationId.isValid("abc\ndef")).isFalse();
            assertThat(CorrelationId.isValid("injected\n2026-07-13 ERROR fake log line")).isFalse();
        }

        @Test
        void rejectsValuesLongerThan128Characters() {
            assertThat(CorrelationId.isValid("a".repeat(129))).isFalse();
        }

        @Test
        void accepts128CharacterValue() {
            assertThat(CorrelationId.isValid("a".repeat(128))).isTrue();
        }

        @Test
        void rejectsNonPrintableOrNonAsciiCharacters() {
            assertThat(CorrelationId.isValid("héllo")).isFalse();
            assertThat(CorrelationId.isValid("コリレーション")).isFalse();
            assertThat(CorrelationId.isValid("abc\u0007def")).isFalse();
            assertThat(CorrelationId.isValid("abc\u007Fdef")).isFalse();
            assertThat(CorrelationId.isValid("abc\u0000def")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "550e8400e29b41d4a716446655440000",
                "req-123",
                "A_b.C~d:e/f",
                "with space inside"
        })
        void acceptsPrintableAsciiValues(String value) {
            assertThat(CorrelationId.isValid(value)).isTrue();
        }
    }
}
