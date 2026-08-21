package io.javalibs.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the machine name written into the {@code host} field.
 *
 * <p>The {@code HOSTNAME} environment variable is preferred because container
 * runtimes set it to the container or pod name — the identity operators
 * actually search by — and because reading it avoids a reverse DNS lookup on
 * the request path. The value is resolved once and cached; a failure degrades
 * to {@value #UNKNOWN} rather than propagating, since no logging concern is
 * worth preventing an application from starting.</p>
 */
public final class LogHost {

    /** Value used when the host name cannot be determined. */
    public static final String UNKNOWN = "unknown";

    private static final String RESOLVED = resolve();

    private LogHost() {
        // static utility
    }

    /**
     * Returns the cached host name.
     *
     * @return the host name, never {@code null} or blank
     */
    public static String current() {
        return RESOLVED;
    }

    private static String resolve() {
        String fromEnvironment = System.getenv("HOSTNAME");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            return (hostName != null && !hostName.isBlank()) ? hostName : UNKNOWN;
        } catch (UnknownHostException | RuntimeException ex) {
            return UNKNOWN;
        }
    }
}
