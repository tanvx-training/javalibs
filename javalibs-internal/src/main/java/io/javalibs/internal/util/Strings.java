package io.javalibs.internal.util;

import java.util.Locale;

/**
 * String utilities shared across javalibs modules.
 *
 * <p>Pure Java, no framework dependencies. Not a replacement for a full-blown
 * commons library — only the helpers the javalibs modules actually need.
 */
public final class Strings {

    private Strings() {
    }

    /** Returns {@code true} when the value is {@code null}, empty or whitespace only. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Returns {@code true} when the value contains at least one non-whitespace character. */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /** Returns {@code value} when it is not blank, otherwise {@code defaultValue}. */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * Truncates the value to {@code maxLength} characters. Returns the value
     * unchanged when it is {@code null} or already short enough.
     */
    public static String truncate(String value, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be >= 0, got " + maxLength);
        }
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Converts camelCase / PascalCase to lower snake_case
     * (e.g. {@code "createdAt" -> "created_at"}).
     */
    public static String toSnakeCase(String value) {
        if (isBlank(value)) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && value.charAt(i - 1) != '_') {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts snake_case or kebab-case to camelCase
     * (e.g. {@code "created_at" -> "createdAt"}).
     */
    public static String toCamelCase(String value) {
        if (isBlank(value)) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '-') {
                upperNext = !sb.isEmpty();
                continue;
            }
            if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Masks the middle of a sensitive value, keeping {@code visible} characters on
     * each side (e.g. {@code mask("0912345678", 2) -> "09******78"}). Values shorter
     * than {@code 2 * visible + 1} are fully masked to avoid leaking short secrets.
     */
    public static String mask(String value, int visible) {
        if (value == null) {
            return null;
        }
        if (visible < 0) {
            throw new IllegalArgumentException("visible must be >= 0, got " + visible);
        }
        if (value.length() < 2 * visible + 1) {
            return "*".repeat(value.length());
        }
        return value.substring(0, visible)
                + "*".repeat(value.length() - 2 * visible)
                + value.substring(value.length() - visible);
    }

    /**
     * Masks an email address keeping the first character of the local part and the
     * full domain (e.g. {@code "john.doe@acme.com" -> "j*******@acme.com"}).
     * Non-email values are fully masked.
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "*".repeat(email.length());
        }
        return email.charAt(0) + "*".repeat(Math.max(at - 1, 1)) + email.substring(at);
    }

    /** Null-safe lowercase using {@link Locale#ROOT} (stable across system locales). */
    public static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Null-safe uppercase using {@link Locale#ROOT} (stable across system locales). */
    public static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
