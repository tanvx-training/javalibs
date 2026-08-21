package io.javalibs.logging.spring;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
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
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

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
 *
 * <p>Async requests (a controller returning {@code DeferredResult}, {@code
 * Callable}, {@code StreamingResponseBody}, or an SSE emitter) go through this
 * filter twice: once when the handler starts the async work and yields, and
 * once more, on the container's re-dispatch, when the result is actually
 * ready. Logging — and, when {@link AccessLogSettings#includeBody()} is on,
 * flushing the cached response body back to the client — is deferred to
 * whichever of those two passes is the real end of the request, identified via
 * {@link #isAsyncStarted(HttpServletRequest)}. Doing this on the first pass
 * would record a fake {@code 200} status at close to {@code 0ms} and, worse,
 * would leave {@link ContentCachingResponseWrapper#copyBodyToResponse()}
 * uncalled forever, since the container does not run this filter again once
 * async processing is under way unless {@link #shouldNotFilterAsyncDispatch()}
 * says otherwise.</p>
 */
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);

    /** Request attribute holding the {@link System#nanoTime()} of the first dispatch. */
    private static final String ATTR_STARTED_AT_NANOS = HttpAccessLogFilter.class.getName() + ".startedAtNanos";

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

    /**
     * Participates in the async re-dispatch instead of being skipped by it —
     * the default {@code true} would mean nobody ever calls
     * {@link ContentCachingResponseWrapper#copyBodyToResponse()} for an async
     * request, leaving the client with an empty response body forever.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        // Only wrap on the dispatch that actually runs the handler. Wrapping
        // again on the async re-dispatch would both be redundant (the wrapper
        // from the first pass is still attached to the request/response the
        // container hands back) and, for the response, would throw away
        // whatever the async handler already wrote into it.
        if (settings.includeBody() && !isAsyncDispatch(request)) {
            requestToUse = (request instanceof ContentCachingRequestWrapper cached) ? cached
                    : new ContentCachingRequestWrapper(request, settings.maxBodyLength());
            responseToUse = (response instanceof ContentCachingResponseWrapper cached) ? cached
                    : new ContentCachingResponseWrapper(response);
        }

        String previousUserId = MDC.get(LogFields.MDC_USER_ID);
        String previousClientIp = MDC.get(LogFields.MDC_CLIENT_IP);
        String previousTags = MDC.get(LogFields.MDC_TAGS);
        long startedAt = startedAtOf(requestToUse);
        try {
            // Every MDC mutation lives inside this try so that a failure in
            // resolving the principal, the client IP, or MDC itself still
            // reaches the finally below and restores what was there before —
            // otherwise userId/clientIp/logTags would stick to this pooled
            // thread and leak into whatever unrelated request runs on it
            // next. logTags in particular is also written by application code
            // via LogContext.tags(), so a caller who forgets the
            // try-with-resources on that scope relies on this restore to
            // avoid tags accumulating across requests forever.
            putOrRemove(LogFields.MDC_USER_ID, resolveUserId(requestToUse));
            putOrRemove(LogFields.MDC_CLIENT_IP,
                    clientIpResolver.resolve(requestToUse::getHeader, requestToUse.getRemoteAddr()));
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            try {
                // The handler may have just started async processing and
                // returned without producing a final status or body yet. Wait
                // for the dispatch where that is no longer true before we log
                // or flush the cached response.
                if (!isAsyncStarted(requestToUse)) {
                    long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    try {
                        logExchange(requestToUse, responseToUse, latencyMs);
                    } catch (RuntimeException ex) {
                        log.warn("Could not write the access log entry for {} {}",
                                request.getMethod(), request.getRequestURI(), ex);
                    } finally {
                        copyCachedBody(responseToUse);
                    }
                }
            } finally {
                putOrRemove(LogFields.MDC_USER_ID, previousUserId);
                putOrRemove(LogFields.MDC_CLIENT_IP, previousClientIp);
                putOrRemove(LogFields.MDC_TAGS, previousTags);
            }
        }
    }

    private static long startedAtOf(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTR_STARTED_AT_NANOS);
        if (existing instanceof Long nanos) {
            return nanos;
        }
        long now = System.nanoTime();
        request.setAttribute(ATTR_STARTED_AT_NANOS, now);
        return now;
    }

    private void logExchange(HttpServletRequest request, HttpServletResponse response, long latencyMs) {
        String endpoint = endpointOf(request);
        HttpRequestLog requestLog = new HttpRequestLog(request.getMethod(), endpoint,
                headersOf(request), requestBodyOf(request));
        HttpResponseLog responseLog =
                new HttpResponseLog(response.getStatus(), latencyMs, responseBodyOf(response));

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

    private static void copyCachedBody(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            try {
                wrapper.copyBodyToResponse();
            } catch (IOException ex) {
                log.warn("Could not copy the cached response body back to the client", ex);
            }
        }
    }

    private Object requestBodyOf(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        return bodyOf(wrapper.getContentAsByteArray(), wrapper.getContentType(), wrapper.getCharacterEncoding());
    }

    private Object responseBodyOf(HttpServletResponse response) {
        if (!(response instanceof ContentCachingResponseWrapper wrapper)) {
            return null;
        }
        return bodyOf(wrapper.getContentAsByteArray(), wrapper.getContentType(), wrapper.getCharacterEncoding());
    }

    /**
     * Renders a captured payload. JSON is parsed into a map so it nests properly
     * in the log record; form data is masked field by field; anything binary is
     * dropped entirely rather than being turned into mojibake.
     *
     * <p>Truncation is detected by comparing the captured byte count against
     * {@link AccessLogSettings#maxBodyLength()} rather than by asking the JSON
     * parser to fail on a cut-off document: {@code ContentCachingRequestWrapper}
     * is constructed with that same number as its cache limit and silently
     * stops caching once the buffer reaches it, so a captured array at or past
     * the limit means the source was at least that long. The comparison holds
     * for every source — a declared {@code Content-Length}, a chunked body with
     * none, or an application that never fully read the stream — and also holds
     * regardless of whether the JSON parser on the
     * classpath happens to reject malformed input or, as Spring Boot's built-in
     * {@code BasicJsonParser} does when no richer parser (Jackson, Gson,
     * Jettison) is present, silently returns whatever partial structure it
     * could read from the fragment.</p>
     *
     * <p>A truncated body is never hydrated as a partial string, either. A cut
     * JSON document such as {@code {"password":"hunter2"...} } masks nothing —
     * masking is applied by walking the parsed key/value pairs, and a string
     * has no such structure — so handing out the raw fragment would print
     * whatever secret happened to land inside the kept prefix straight into the
     * log. It is replaced with a sized placeholder instead: the field stays
     * present and the reader can tell the body was cut, without any byte of the
     * actual content leaving process memory. Form-encoded bodies are the one
     * exception, because they are masked value-by-value regardless of where the
     * cut lands, so the (possibly partial) masked string is still safe to log.</p>
     */
    private Object bodyOf(byte[] content, String contentType, String encoding) {
        if (content == null || content.length == 0) {
            return null;
        }
        String type = (contentType != null) ? contentType.toLowerCase(Locale.ROOT) : "";
        if (!isTextual(type)) {
            return null;
        }
        boolean truncated = content.length >= settings.maxBodyLength();
        if (truncated && !isFormEncoded(type)) {
            return "<truncated " + settings.maxBodyLength() + " bytes>";
        }
        String raw = new String(content, charsetOf(type, encoding));
        if (!truncated && isJson(type)) {
            try {
                return JsonParserFactory.getJsonParser().parseMap(raw);
            } catch (RuntimeException ex) {
                return raw;
            }
        }
        if (isFormEncoded(type)) {
            return masker.maskFormEncoded(raw);
        }
        return raw;
    }

    private static boolean isTextual(String contentType) {
        return isJson(contentType) || isFormEncoded(contentType) || contentType.startsWith("text/");
    }

    /** Matches {@code application/json} and structured-syntax suffixes such as {@code application/problem+json}. */
    private static boolean isJson(String contentType) {
        return contentType.startsWith("application/json")
                || (contentType.startsWith("application/") && contentType.contains("+json"));
    }

    private static boolean isFormEncoded(String contentType) {
        return contentType.startsWith("application/x-www-form-urlencoded");
    }

    /**
     * Picks the charset to decode a captured body with.
     *
     * <p>{@code ContentCachingRequestWrapper#getCharacterEncoding()} never
     * returns {@code null} — it falls back to {@code ISO-8859-1}
     * ({@link org.springframework.web.util.WebUtils#DEFAULT_CHARACTER_ENCODING})
     * whenever the request did not declare one, which is the common case for a
     * JSON body: RFC 8259 makes UTF-8 the default for {@code application/json}
     * and few clients bother stating it. Believing the servlet-level fallback
     * for JSON therefore corrupts every non-ASCII character. JSON without an
     * explicit {@code charset} parameter is decoded as UTF-8 instead; every
     * other declared or default encoding is honoured as reported.
     */
    private static Charset charsetOf(String contentType, String encoding) {
        if (isJson(contentType) && !contentType.contains("charset=")) {
            return StandardCharsets.UTF_8;
        }
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }
}
