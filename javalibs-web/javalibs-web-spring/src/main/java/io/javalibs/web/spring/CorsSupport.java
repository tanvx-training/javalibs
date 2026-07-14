package io.javalibs.web.spring;

import java.util.List;

import org.springframework.web.cors.CorsConfiguration;

/**
 * Static helper building a Spring {@link CorsConfiguration} from simple values.
 *
 * <p>When {@code allowCredentials} is {@code true} the origins are registered as
 * <em>origin patterns</em> ({@link CorsConfiguration#setAllowedOriginPatterns}) because
 * Spring rejects the {@code *} wildcard origin in combination with credentials.</p>
 */
public final class CorsSupport {

    private CorsSupport() {
    }

    /**
     * Builds a {@link CorsConfiguration} from the given values. {@code null} or empty
     * lists leave the corresponding setting untouched.
     *
     * @param allowedOrigins   origins (or origin patterns when credentials are allowed)
     * @param allowedMethods   allowed HTTP methods, e.g. {@code GET, POST}
     * @param allowedHeaders   allowed request headers, {@code *} for any
     * @param exposedHeaders   response headers exposed to the browser
     * @param allowCredentials whether cookies/authorization headers are allowed
     * @param maxAgeSeconds    how long the preflight response may be cached, in seconds
     * @return a populated {@link CorsConfiguration}
     */
    public static CorsConfiguration build(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAgeSeconds) {

        CorsConfiguration config = new CorsConfiguration();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            if (allowCredentials) {
                config.setAllowedOriginPatterns(allowedOrigins);
            } else {
                config.setAllowedOrigins(allowedOrigins);
            }
        }
        if (allowedMethods != null && !allowedMethods.isEmpty()) {
            config.setAllowedMethods(allowedMethods);
        }
        if (allowedHeaders != null && !allowedHeaders.isEmpty()) {
            config.setAllowedHeaders(allowedHeaders);
        }
        if (exposedHeaders != null && !exposedHeaders.isEmpty()) {
            config.setExposedHeaders(exposedHeaders);
        }
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAgeSeconds);
        return config;
    }
}
