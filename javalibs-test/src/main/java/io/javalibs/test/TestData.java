package io.javalibs.test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic-free random test data factory. Values are random per call —
 * never assert on their concrete content, only on behavior.
 */
public final class TestData {

    private static final String ALPHANUMERIC =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] FIRST_NAMES =
            {"An", "Binh", "Chi", "Dung", "Hoa", "Khanh", "Linh", "Minh", "Nam", "Trang"};
    private static final String[] LAST_NAMES =
            {"Nguyen", "Tran", "Le", "Pham", "Hoang", "Vu", "Dang", "Bui", "Do", "Ngo"};

    private TestData() {
    }

    /** Random alphanumeric string of the given length. */
    public static String string(int length) {
        var random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /** Random unique email under the reserved example.com domain. */
    public static String email() {
        return "user-" + string(10) + "@example.com";
    }

    /** Random plausible full name. */
    public static String fullName() {
        var random = ThreadLocalRandom.current();
        return LAST_NAMES[random.nextInt(LAST_NAMES.length)] + " "
                + FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
    }

    /** Random int in {@code [minInclusive, maxExclusive)}. */
    public static int intBetween(int minInclusive, int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    /** Random UUID string. */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** Random instant within the past {@code window}, truncated to milliseconds. */
    public static Instant instantInPast(Duration window) {
        long millis = ThreadLocalRandom.current().nextLong(window.toMillis());
        return Instant.ofEpochMilli(System.currentTimeMillis() - millis);
    }
}
