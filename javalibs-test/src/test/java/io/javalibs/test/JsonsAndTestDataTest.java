package io.javalibs.test;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonsAndTestDataTest {

    record Sample(String name, int count) {
    }

    @Test
    void jsonRoundTrip() {
        Sample sample = new Sample("abc", 3);
        String json = Jsons.toJson(sample);
        assertThat(Jsons.fromJson(json, Sample.class)).isEqualTo(sample);
    }

    @Test
    void equivalenceIgnoresKeyOrderAndWhitespace() {
        Jsons.assertEquivalent(
                "{\"a\": 1, \"b\": [1, 2]}",
                "{ \"b\":[1,2], \"a\":1 }");
        assertThatThrownBy(() -> Jsons.assertEquivalent("{\"a\":1}", "{\"a\":2}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("differ");
    }

    @Test
    void testDataShapes() {
        assertThat(TestData.string(12)).hasSize(12).matches("[0-9a-zA-Z]{12}");
        assertThat(TestData.email()).matches("user-[0-9a-zA-Z]{10}@example\\.com");
        assertThat(TestData.fullName()).contains(" ");
        assertThat(TestData.intBetween(5, 10)).isBetween(5, 9);
        assertThat(TestData.instantInPast(Duration.ofDays(1)))
                .isBefore(Instant.now().plusSeconds(1))
                .isAfter(Instant.now().minus(Duration.ofDays(2)));
    }
}
