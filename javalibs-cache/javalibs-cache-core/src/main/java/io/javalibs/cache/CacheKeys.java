package io.javalibs.cache;

/**
 * Platform-wide cache key convention.
 *
 * <p>Full Redis keys follow the format
 * {@code <app-prefix>::<cacheName>::<part1>:<part2>:...}. The application prefix
 * and cache name segments are applied automatically by the javalibs cache
 * configuration; application code only builds the trailing key part with
 * {@link #join(String...)}:
 *
 * <pre>{@code
 * @Cacheable(cacheNames = "orders", key = "T(io.javalibs.cache.CacheKeys).join('by-customer', #customerId)")
 * }</pre>
 */
public final class CacheKeys {

    /** Separator between the prefix, cache name and key sections of a full key. */
    public static final String SECTION_SEPARATOR = "::";

    /** Separator between the parts of the key section. */
    public static final char PART_SEPARATOR = ':';

    private CacheKeys() {
    }

    /**
     * Joins key parts with {@code ':'} after validating each part: parts must be
     * non-blank, contain no whitespace/control characters and must not contain
     * the reserved {@code "::"} section separator.
     *
     * @param parts key parts, e.g. {@code join("by-customer", "42") -> "by-customer:42"}
     * @return the joined key section
     * @throws IllegalArgumentException when no parts are given or a part is invalid
     */
    public static String join(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("At least one key part is required");
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            validate(part);
            if (!sb.isEmpty()) {
                sb.append(PART_SEPARATOR);
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static void validate(String part) {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("Cache key parts must not be blank");
        }
        if (part.contains(SECTION_SEPARATOR)) {
            throw new IllegalArgumentException(
                    "Cache key parts must not contain the reserved separator \"::\": " + part);
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException(
                        "Cache key parts must not contain whitespace or control characters: " + part);
            }
        }
    }
}
