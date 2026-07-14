package io.javalibs.internal.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Date/time helpers standardizing on ISO-8601 and UTC across javalibs modules.
 */
public final class Dates {

    /** ISO-8601 formatter with millisecond precision in UTC, e.g. {@code 2026-07-13T08:30:00.000Z}. */
    public static final DateTimeFormatter ISO_MILLIS_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

    private Dates() {
    }

    /** Current time truncated to milliseconds (stable JSON round-trips across databases). */
    public static Instant nowMillis() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Formats an instant as ISO-8601 UTC with millisecond precision. */
    public static String formatIso(Instant instant) {
        return instant == null ? null : ISO_MILLIS_UTC.format(instant);
    }

    /** Parses an ISO-8601 instant (e.g. {@code 2026-07-13T08:30:00Z}). */
    public static Instant parseIso(String value) {
        return Strings.isBlank(value) ? null : Instant.parse(value.trim());
    }

    /** Converts a local date-time in the given zone to an instant. */
    public static Instant toInstant(LocalDateTime dateTime, ZoneId zone) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(Checks.notNull(zone, "zone")).toInstant();
    }

    /** Converts an instant to a local date in the given zone. */
    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(Checks.notNull(zone, "zone")).toLocalDate();
    }

    /** Whole days between two instants (truncating, may be negative). */
    public static long daysBetween(Instant from, Instant to) {
        return ChronoUnit.DAYS.between(
                Checks.notNull(from, "from"), Checks.notNull(to, "to"));
    }
}
