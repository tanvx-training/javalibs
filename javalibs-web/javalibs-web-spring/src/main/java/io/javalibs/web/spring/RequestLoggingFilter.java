package io.javalibs.web.spring;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter logging one INFO line per HTTP request: method, URI (with query string),
 * response status and duration in milliseconds.
 *
 * <p>When {@code includePayload} is enabled the request and response are wrapped with
 * content-caching wrappers and their bodies are appended to the log line, truncated to
 * {@code maxPayloadLength} characters. Payloads are only logged for textual content types
 * ({@code application/json} and {@code text/*}); binary content is never logged.</p>
 *
 * <p>Requests matching any of the {@code excludedPaths} Ant patterns (default
 * {@code /actuator/**}) are not logged at all.</p>
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** Default maximum number of payload characters appended to the log line. */
    public static final int DEFAULT_MAX_PAYLOAD_LENGTH = 2048;

    /** Default set of excluded Ant path patterns. */
    public static final List<String> DEFAULT_EXCLUDED_PATHS = List.of("/actuator/**");

    private final boolean includePayload;
    private final int maxPayloadLength;
    private final List<String> excludedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Creates a filter with default settings: payload logging disabled, max payload
     * length of {@value #DEFAULT_MAX_PAYLOAD_LENGTH} and {@code /actuator/**} excluded.
     */
    public RequestLoggingFilter() {
        this(false, DEFAULT_MAX_PAYLOAD_LENGTH, DEFAULT_EXCLUDED_PATHS);
    }

    /**
     * Creates a fully configured filter.
     *
     * @param includePayload   whether request/response bodies should be logged
     * @param maxPayloadLength maximum number of payload characters to log
     * @param excludedPaths    Ant patterns of request paths that must not be logged;
     *                         {@code null} falls back to {@link #DEFAULT_EXCLUDED_PATHS}
     */
    public RequestLoggingFilter(boolean includePayload, int maxPayloadLength, List<String> excludedPaths) {
        this.includePayload = includePayload;
        this.maxPayloadLength = maxPayloadLength > 0 ? maxPayloadLength : DEFAULT_MAX_PAYLOAD_LENGTH;
        this.excludedPaths = excludedPaths != null ? List.copyOf(excludedPaths) : DEFAULT_EXCLUDED_PATHS;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        if (includePayload) {
            if (!(request instanceof ContentCachingRequestWrapper)) {
                requestToUse = new ContentCachingRequestWrapper(request, maxPayloadLength);
            }
            if (!(response instanceof ContentCachingResponseWrapper)) {
                responseToUse = new ContentCachingResponseWrapper(response);
            }
        }

        long start = System.nanoTime();
        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            logRequest(requestToUse, responseToUse, durationMs);
            if (responseToUse instanceof ContentCachingResponseWrapper wrapper) {
                wrapper.copyBodyToResponse();
            }
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String query = request.getQueryString();
        String uri = query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();

        StringBuilder line = new StringBuilder(128)
                .append(request.getMethod()).append(' ').append(uri)
                .append(" status=").append(response.getStatus())
                .append(" duration=").append(durationMs).append("ms");

        if (includePayload) {
            String requestBody = extractRequestPayload(request);
            if (requestBody != null && !requestBody.isEmpty()) {
                line.append(" request=").append(requestBody);
            }
            String responseBody = extractResponsePayload(response);
            if (responseBody != null && !responseBody.isEmpty()) {
                line.append(" response=").append(responseBody);
            }
        }
        log.info("{}", line);
    }

    private String extractRequestPayload(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper && isLoggableContentType(wrapper.getContentType())) {
            return truncate(wrapper.getContentAsByteArray(), charsetOf(wrapper.getCharacterEncoding()));
        }
        return null;
    }

    private String extractResponsePayload(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper
                && isLoggableContentType(wrapper.getContentType())) {
            return truncate(wrapper.getContentAsByteArray(), charsetOf(wrapper.getCharacterEncoding()));
        }
        return null;
    }

    private static boolean isLoggableContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/json") || normalized.startsWith("text/");
    }

    private String truncate(byte[] content, Charset charset) {
        if (content == null || content.length == 0) {
            return null;
        }
        String payload = new String(content, charset);
        return payload.length() > maxPayloadLength ? payload.substring(0, maxPayloadLength) : payload;
    }

    private static Charset charsetOf(String encoding) {
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }
}
