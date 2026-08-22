package io.javalibs.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Masks secrets inside {@code application/x-www-form-urlencoded} payloads and
 * query strings.
 *
 * <p>Structured JSON payloads are masked by the logging formatter itself, which
 * walks the object graph and consults {@link SensitiveKeys} for every value it
 * writes. A form-encoded body has no such structure once it reaches the log
 * record — it is a single string — so it is handled here instead.</p>
 */
public final class SensitiveDataMasker {

    /** Replacement written in place of a secret. */
    public static final String DEFAULT_MASK = "********";

    private final SensitiveKeys keys;
    private final String mask;

    /**
     * Creates a masker.
     *
     * @param keys the sensitive key policy; {@code null} falls back to
     *             {@link SensitiveKeys#defaults()}
     * @param mask the replacement text; {@code null} or blank falls back to
     *             {@link #DEFAULT_MASK}
     */
    public SensitiveDataMasker(SensitiveKeys keys, String mask) {
        this.keys = (keys != null) ? keys : SensitiveKeys.defaults();
        this.mask = (mask != null && !mask.isBlank()) ? mask : DEFAULT_MASK;
    }

    /**
     * Replaces the value of every sensitive field in a form-encoded string.
     *
     * <p>Field names are percent-decoded before being matched, so
     * {@code api%5Fkey} is recognised as {@code api_key}. The original encoding
     * of the name is preserved in the output.</p>
     *
     * @param body the form-encoded body or query string, may be {@code null}
     * @return the body with sensitive values replaced; {@code null} in,
     *         {@code null} out
     */
    public String maskFormEncoded(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        StringBuilder masked = new StringBuilder(body.length());
        int start = 0;
        while (true) {
            int ampersand = body.indexOf('&', start);
            int end = (ampersand >= 0) ? ampersand : body.length();
            appendPair(masked, body, start, end);
            if (ampersand < 0) {
                return masked.toString();
            }
            masked.append('&');
            start = ampersand + 1;
        }
    }

    private void appendPair(StringBuilder masked, String body, int start, int end) {
        int equals = body.indexOf('=', start);
        if (equals < 0 || equals >= end) {
            masked.append(body, start, end);
            return;
        }
        String name = body.substring(start, equals);
        masked.append(name).append('=');
        if (keys.isSensitive(decode(name))) {
            masked.append(mask);
        } else {
            masked.append(body, equals + 1, end);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
