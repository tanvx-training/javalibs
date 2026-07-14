package io.javalibs.internal.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class IdsAndDatesTest {

    @Test
    void idFormats() {
        assertThat(Ids.uuid()).hasSize(36).contains("-");
        assertThat(Ids.compactUuid()).hasSize(32).doesNotContain("-");
        assertThat(Ids.shortId(8)).hasSize(8).matches("[0-9A-Za-z]{8}");
        assertThat(Ids.uuid()).isNotEqualTo(Ids.uuid());
    }

    @Test
    void isoRoundTrip() {
        Instant now = Dates.nowMillis();
        assertThat(Dates.parseIso(Dates.formatIso(now))).isEqualTo(now);
        assertThat(Dates.formatIso(null)).isNull();
        assertThat(Dates.parseIso(" ")).isNull();
    }

    @Test
    void conversions() {
        LocalDateTime ldt = LocalDateTime.of(2026, 7, 13, 10, 0);
        Instant instant = Dates.toInstant(ldt, ZoneOffset.UTC);
        assertThat(instant).isEqualTo(Instant.parse("2026-07-13T10:00:00Z"));
        assertThat(Dates.toLocalDate(instant, ZoneOffset.UTC)).isEqualTo("2026-07-13");
        assertThat(Dates.daysBetween(Instant.parse("2026-07-10T00:00:00Z"),
                Instant.parse("2026-07-13T00:00:00Z"))).isEqualTo(3);
    }
}
