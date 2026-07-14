package io.javalibs.observability;

import java.util.UUID;

/**
 * Utility for generating and validating correlation ids.
 *
 * <p>A correlation id is an opaque token that follows a logical request across
 * services, threads and log lines. It is usually transported in the
 * {@link #DEFAULT_HEADER X-Correlation-Id} HTTP header and exposed to logging
 * frameworks through the {@link #MDC_KEY correlationId} MDC key.</p>
 *
 * <p>{@link #isValid(String)} deliberately rejects values containing CR/LF or
 * any non-printable/non-ASCII character to defend against log injection and
 * HTTP response splitting when an incoming header value is echoed back.</p>
 */
public final class CorrelationId {

    /** Default HTTP header used to transport the correlation id. */
    public static final String DEFAULT_HEADER = "X-Correlation-Id";

    /** MDC key under which the correlation id is published for log patterns. */
    public static final String MDC_KEY = "correlationId";

    /** Maximum accepted length of an externally supplied correlation id. */
    private static final int MAX_LENGTH = 128;

    private CorrelationId() {
        // static utility
    }

    /**
     * Generates a new random correlation id.
     *
     * @return a random UUID rendered without dashes (32 lower-case hex characters)
     */
    public static String generate() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString().replace("-", "");
    }

    /**
     * Checks whether an externally supplied correlation id is safe to reuse.
     *
     * <p>A value is valid when it is non-null, non-blank, at most 128
     * characters long and consists exclusively of printable ASCII characters
     * (0x20-0x7E). Carriage returns, line feeds, other control characters and
     * non-ASCII characters are rejected to prevent log injection.</p>
     *
     * @param value the candidate correlation id, may be {@code null}
     * @return {@code true} if the value can safely be reused, {@code false} otherwise
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }
}
