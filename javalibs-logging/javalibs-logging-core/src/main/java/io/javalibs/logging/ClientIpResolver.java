package io.javalibs.logging;

import java.util.function.Function;

/**
 * Resolves the IP address to record as the {@code ip} field of a log event.
 *
 * <p>Proxy headers are only consulted when {@code trustProxy} is enabled. That
 * default matters: {@code X-Forwarded-For} is attacker-controlled on a directly
 * exposed application, so trusting it unconditionally would let any caller
 * choose the address written into the audit trail. Turn it on only when the
 * application really does sit behind a reverse proxy that overwrites the
 * header.</p>
 *
 * <p>Values are validated to contain nothing but hex digits, dots, colons and
 * {@code %} (the IPv6 zone separator). Anything else — including the CR/LF that
 * would allow log injection — is rejected in favour of the transport level
 * address.</p>
 */
public final class ClientIpResolver {

    /** Header carrying the proxy chain, client first. */
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /** Header carrying a single client address, set by some proxies. */
    public static final String HEADER_REAL_IP = "X-Real-IP";

    /** Longest possible textual IPv6 address including a zone index. */
    private static final int MAX_LENGTH = 45;

    private final boolean trustProxy;

    /**
     * Creates a resolver.
     *
     * @param trustProxy whether {@code X-Forwarded-For} / {@code X-Real-IP} may
     *                   be believed
     */
    public ClientIpResolver(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    /**
     * Resolves the client address.
     *
     * @param headerLookup function returning a request header by name, may be
     *                     {@code null}
     * @param remoteAddress the transport level peer address, may be {@code null}
     * @return the resolved address, or {@code null} when none is usable
     */
    public String resolve(Function<String, String> headerLookup, String remoteAddress) {
        if (trustProxy && headerLookup != null) {
            String forwarded = firstEntry(headerLookup.apply(HEADER_FORWARDED_FOR));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = sanitize(headerLookup.apply(HEADER_REAL_IP));
            if (realIp != null) {
                return realIp;
            }
        }
        return sanitize(remoteAddress);
    }

    private static String firstEntry(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return sanitize((comma >= 0) ? headerValue.substring(0, comma) : headerValue);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
