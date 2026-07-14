package io.javalibs.web.autoconfigure;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the javalibs web layer, bound from the
 * {@code javalibs.web.*} namespace.
 *
 * @param exceptionHandler global exception handler settings
 * @param logging          request logging filter settings
 * @param cors             CORS filter settings
 */
@ConfigurationProperties("javalibs.web")
public record WebProperties(
        @DefaultValue ExceptionHandler exceptionHandler,
        @DefaultValue Logging logging,
        @DefaultValue Cors cors) {

    /**
     * Settings for the global exception handler.
     *
     * @param enabled whether the {@code GlobalExceptionHandler} bean is registered
     *                (default {@code true})
     */
    public record ExceptionHandler(@DefaultValue("true") boolean enabled) {
    }

    /**
     * Settings for the request logging filter.
     *
     * @param enabled          whether the logging filter is registered (default {@code true})
     * @param includePayload   whether request/response bodies are logged (default {@code false})
     * @param maxPayloadLength maximum number of payload characters logged (default {@code 2048})
     * @param excludedPaths    Ant patterns excluded from logging (default {@code /actuator/**})
     */
    public record Logging(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean includePayload,
            @DefaultValue("2048") int maxPayloadLength,
            @DefaultValue("/actuator/**") List<String> excludedPaths) {
    }

    /**
     * Settings for the CORS filter.
     *
     * @param enabled          whether the CORS filter is registered (default {@code false})
     * @param allowedOrigins   origins allowed to make cross-origin requests
     * @param allowedMethods   allowed HTTP methods (default GET, POST, PUT, PATCH, DELETE, OPTIONS)
     * @param allowedHeaders   allowed request headers (default {@code *})
     * @param exposedHeaders   response headers exposed to the browser
     * @param allowCredentials whether credentials are allowed (default {@code false})
     * @param maxAge           preflight cache duration in seconds (default {@code 3600})
     * @param path             URL pattern the CORS configuration applies to (default {@code /**})
     */
    public record Cors(
            @DefaultValue("false") boolean enabled,
            List<String> allowedOrigins,
            @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) List<String> allowedMethods,
            @DefaultValue("*") List<String> allowedHeaders,
            List<String> exposedHeaders,
            @DefaultValue("false") boolean allowCredentials,
            @DefaultValue("3600") long maxAge,
            @DefaultValue("/**") String path) {
    }
}
