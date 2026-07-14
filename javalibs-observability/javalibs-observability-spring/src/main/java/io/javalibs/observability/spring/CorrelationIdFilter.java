package io.javalibs.observability.spring;

import java.io.IOException;

import io.javalibs.observability.CorrelationId;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.Assert;

/**
 * Servlet {@link Filter} that establishes a correlation id for every HTTP
 * request.
 *
 * <p>The filter reads the configured request header; if the header is absent
 * or fails {@link CorrelationId#isValid(String)} (guarding against log
 * injection), a fresh id is generated via {@link CorrelationId#generate()}.
 * The id is then published to the SLF4J MDC under
 * {@link CorrelationId#MDC_KEY} and written to the response header
 * <em>before</em> the rest of the chain executes, so it is present even when
 * the response is committed early. The MDC entry is always removed when the
 * request completes.</p>
 *
 * <p>Non-HTTP requests are passed through untouched. This class is a plain
 * {@code jakarta.servlet.Filter} on purpose so it does not require
 * {@code spring-web} on the classpath.</p>
 */
public class CorrelationIdFilter implements Filter {

    private final String headerName;

    /**
     * Creates a filter reading and writing the given header.
     *
     * @param headerName the HTTP header transporting the correlation id, must not be blank
     */
    public CorrelationIdFilter(String headerName) {
        Assert.hasText(headerName, "headerName must not be blank");
        this.headerName = headerName;
    }

    /**
     * Returns the HTTP header name this filter reads and writes.
     *
     * @return the configured header name
     */
    public String getHeaderName() {
        return this.headerName;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String incoming = httpRequest.getHeader(this.headerName);
        String correlationId = CorrelationId.isValid(incoming) ? incoming : CorrelationId.generate();

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            httpResponse.setHeader(this.headerName, correlationId);
            chain.doFilter(request, response);
        }
        finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
