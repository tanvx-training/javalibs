package io.javalibs.logging.spring;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import io.javalibs.logging.ClientIpResolver;
import io.javalibs.logging.HttpRequestLog;
import io.javalibs.logging.HttpResponseLog;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits one structured access log event per HTTP request and publishes the
 * request-scoped {@code userId} / {@code clientIp} MDC entries that every other
 * log line in the request picks up for free.
 *
 * <p>The {@code request} and {@code response} subtrees travel as SLF4J
 * key-value pairs, which the javalibs formatter renders as nested JSON objects.
 * The message itself stays human readable ({@code POST /api/login 200 152ms}),
 * so an application that has not switched JSON logging on still gets a sensible
 * plain-text line rather than an empty one.</p>
 */
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);

    private final AccessLogSettings settings;
    private final PrincipalResolver principalResolver;
    private final SensitiveDataMasker masker;
    private final ClientIpResolver clientIpResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Creates the filter.
     *
     * @param settings          filter settings; {@code null} uses
     *                          {@link AccessLogSettings#defaults()}
     * @param principalResolver resolver for {@code user_id}; {@code null} uses
     *                          {@link PrincipalResolver#DEFAULT}
     * @param masker            masker for query strings and form bodies;
     *                          {@code null} uses the default key policy
     */
    public HttpAccessLogFilter(AccessLogSettings settings, PrincipalResolver principalResolver,
            SensitiveDataMasker masker) {
        this.settings = (settings != null) ? settings : AccessLogSettings.defaults();
        this.principalResolver = (principalResolver != null) ? principalResolver : PrincipalResolver.DEFAULT;
        this.masker = (masker != null) ? masker
                : new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK);
        this.clientIpResolver = new ClientIpResolver(this.settings.trustProxy());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return settings.excludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String previousUserId = MDC.get(LogFields.MDC_USER_ID);
        String previousClientIp = MDC.get(LogFields.MDC_CLIENT_IP);
        putOrRemove(LogFields.MDC_USER_ID, resolveUserId(request));
        putOrRemove(LogFields.MDC_CLIENT_IP,
                clientIpResolver.resolve(request::getHeader, request.getRemoteAddr()));

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
            try {
                logExchange(request, response, latencyMs);
            } catch (RuntimeException ex) {
                log.warn("Could not write the access log entry for {} {}",
                        request.getMethod(), request.getRequestURI(), ex);
            } finally {
                putOrRemove(LogFields.MDC_USER_ID, previousUserId);
                putOrRemove(LogFields.MDC_CLIENT_IP, previousClientIp);
            }
        }
    }

    private void logExchange(HttpServletRequest request, HttpServletResponse response, long latencyMs) {
        String endpoint = endpointOf(request);
        HttpRequestLog requestLog =
                new HttpRequestLog(request.getMethod(), endpoint, headersOf(request), null);
        HttpResponseLog responseLog = new HttpResponseLog(response.getStatus(), latencyMs, null);

        boolean slow = settings.slowThresholdMs() > 0 && latencyMs >= settings.slowThresholdMs();
        LoggingEventBuilder builder = slow ? log.atWarn() : log.atInfo();
        builder.addKeyValue(LogFields.REQUEST, requestLog.toMap())
                .addKeyValue(LogFields.RESPONSE, responseLog.toMap())
                .log("{} {} {} {}ms", request.getMethod(), endpoint, response.getStatus(), latencyMs);
    }

    private String endpointOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null || query.isEmpty()) ? uri : uri + "?" + masker.maskFormEncoded(query);
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        if (!settings.includeHeaders()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : settings.includedHeaders()) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private String resolveUserId(HttpServletRequest request) {
        try {
            return principalResolver.resolve(request);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
