package io.javalibs.internal.util;

import java.util.Collection;
import java.util.Map;

/**
 * Precondition checks used across javalibs modules.
 *
 * <p>All methods throw {@link IllegalArgumentException} (or
 * {@link IllegalStateException} for {@link #state}) with the supplied message and
 * return the checked value so calls can be inlined in assignments and
 * constructors.
 */
public final class Checks {

    private Checks() {
    }

    /** Ensures the value is not {@code null}. */
    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /** Ensures the value is neither {@code null} nor blank. */
    public static String notBlank(String value, String name) {
        if (Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** Ensures the collection is neither {@code null} nor empty. */
    public static <C extends Collection<?>> C notEmpty(C collection, String name) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return collection;
    }

    /** Ensures the map is neither {@code null} nor empty. */
    public static <M extends Map<?, ?>> M notEmpty(M map, String name) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return map;
    }

    /** Ensures an arbitrary argument condition holds. */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Ensures an internal state condition holds. */
    public static void state(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
