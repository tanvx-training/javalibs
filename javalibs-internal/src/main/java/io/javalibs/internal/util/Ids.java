package io.javalibs.internal.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Identifier generation helpers.
 */
public final class Ids {

    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    /** Random UUID as canonical 36-character string. */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** Random UUID without dashes (32 hex characters) — compact for headers and keys. */
    public static String compactUuid() {
        UUID uuid = UUID.randomUUID();
        return String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /**
     * Short random Base62 identifier of the given length (cryptographically random).
     * Suitable for human-facing references, not a replacement for UUIDs.
     */
    public static String shortId(int length) {
        Checks.isTrue(length > 0, "length must be > 0");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62[RANDOM.nextInt(BASE62.length)]);
        }
        return sb.toString();
    }
}
