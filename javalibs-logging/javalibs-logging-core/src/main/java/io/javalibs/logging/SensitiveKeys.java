package io.javalibs.logging;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides whether a field name carries a secret and therefore must be masked.
 *
 * <p>Matching is done on a normalized form of the name — lower-cased with
 * {@code _}, {@code -} and spaces removed — so {@code password}, {@code PASSWORD},
 * {@code Pass_Word} and {@code pass-word} are all recognised by the single
 * default entry {@code password}.</p>
 *
 * <p>Application supplied keys are <em>added to</em> the defaults rather than
 * replacing them, so a service that needs one extra key cannot accidentally
 * unmask everything else.</p>
 */
public final class SensitiveKeys {

    /** Field names masked out of the box. */
    public static final Set<String> DEFAULT_KEYS = Set.of(
            "password", "passwd", "token", "access_token", "refresh_token", "id_token",
            "secret", "client_secret", "authorization", "api_key", "apikey",
            "private_key", "otp", "pin", "card_number", "cvv", "ssn",
            "cookie", "set_cookie", "x_api_key", "proxy_authorization", "session_id",
            "jwt", "secret_key", "api_secret", "credit_card");

    private static final SensitiveKeys DEFAULTS = new SensitiveKeys(Set.of(), true);
    private static final SensitiveKeys NONE = new SensitiveKeys(Set.of(), false);

    private final Set<String> normalized;

    private SensitiveKeys(Collection<String> additional, boolean includeDefaults) {
        Set<String> all = new LinkedHashSet<>();
        if (includeDefaults) {
            for (String key : DEFAULT_KEYS) {
                all.add(normalize(key));
            }
        }
        if (additional != null) {
            for (String key : additional) {
                if (key != null && !key.isBlank()) {
                    all.add(normalize(key));
                }
            }
        }
        this.normalized = Set.copyOf(all);
    }

    /**
     * Returns the shared instance matching only {@link #DEFAULT_KEYS}.
     *
     * @return the default policy
     */
    public static SensitiveKeys defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a policy matching the defaults plus the given extra names.
     *
     * @param additional extra field names to treat as sensitive, may be
     *                   {@code null} or empty
     * @return the combined policy
     */
    public static SensitiveKeys withAdditional(Collection<String> additional) {
        return (additional == null || additional.isEmpty()) ? DEFAULTS : new SensitiveKeys(additional, true);
    }

    /**
     * Returns a policy matching nothing, used when masking is switched off.
     *
     * @return a policy for which {@link #isSensitive(String)} is always {@code false}
     */
    public static SensitiveKeys none() {
        return NONE;
    }

    /**
     * Checks whether a field name is considered sensitive.
     *
     * @param key the field name, may be {@code null}
     * @return {@code true} when the value behind this name must be masked
     */
    public boolean isSensitive(String key) {
        return key != null && !key.isEmpty() && normalized.contains(normalize(key));
    }

    private static String normalize(String key) {
        StringBuilder normalized = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                continue;
            }
            normalized.append(Character.toLowerCase(c));
        }
        return normalized.toString();
    }
}
