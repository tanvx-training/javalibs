package io.javalibs.datahub.spring.rest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ClientHttpRequestInterceptor} that transparently retries transient failures:
 * {@link IOException}s and 5xx responses.
 *
 * <p>A request is attempted up to {@code maxAttempts} times with exponential backoff
 * (the delay starts at {@code initialBackoff} and doubles after each failed attempt,
 * e.g. 200ms then 400ms for three attempts). Each retry is logged at WARN level.</p>
 *
 * <p>By default only idempotent HTTP methods (GET, HEAD, OPTIONS, PUT, DELETE) are
 * retried; non-idempotent methods such as POST pass through untouched unless the
 * {@code retryAllMethods} flag is enabled.</p>
 *
 * <p>When all attempts fail with an {@link IOException} the last exception is
 * rethrown; when the last attempt still yields a 5xx response that response is
 * returned to the caller as-is.</p>
 */
public class RetryingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log =
      LoggerFactory.getLogger(RetryingClientHttpRequestInterceptor.class);

  private static final Set<HttpMethod> IDEMPOTENT_METHODS = Set.of(
      HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.DELETE);

  private final int maxAttempts;
  private final Duration initialBackoff;
  private final boolean retryAllMethods;

  /**
   * Creates an interceptor retrying idempotent methods only.
   *
   * @param maxAttempts    total number of attempts (including the first); at least 1
   * @param initialBackoff delay before the first retry; doubled after each attempt
   */
  public RetryingClientHttpRequestInterceptor(int maxAttempts, Duration initialBackoff) {
    this(maxAttempts, initialBackoff, false);
  }

  /**
   * Creates an interceptor with full control over which methods are retried.
   *
   * @param maxAttempts     total number of attempts (including the first); at least 1
   * @param initialBackoff  delay before the first retry; doubled after each attempt
   * @param retryAllMethods when {@code true} every HTTP method is retried, including
   *                        non-idempotent ones such as POST
   */
  public RetryingClientHttpRequestInterceptor(
      int maxAttempts, Duration initialBackoff, boolean retryAllMethods) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
    if (initialBackoff == null || initialBackoff.isNegative()) {
      throw new IllegalArgumentException("initialBackoff must be a non-negative duration");
    }
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.retryAllMethods = retryAllMethods;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {
    if (!retryAllMethods && !IDEMPOTENT_METHODS.contains(request.getMethod())) {
      return execution.execute(request, body);
    }

    long backoffMillis = initialBackoff.toMillis();
    for (int attempt = 1; ; attempt++) {
      try {
        ClientHttpResponse response = execution.execute(request, body);
        if (!response.getStatusCode().is5xxServerError() || attempt >= maxAttempts) {
          return response;
        }
        log.warn("Retrying {} {} (attempt {} of {} returned {}), backing off {} ms",
            request.getMethod(), request.getURI(), attempt, maxAttempts,
            response.getStatusCode(), backoffMillis);
        response.close();
      } catch (IOException ex) {
        if (attempt >= maxAttempts) {
          throw ex;
        }
        log.warn("Retrying {} {} (attempt {} of {} failed: {}), backing off {} ms",
            request.getMethod(), request.getURI(), attempt, maxAttempts,
            ex.getMessage(), backoffMillis);
      }
      sleep(backoffMillis);
      backoffMillis *= 2;
    }
  }

  private static void sleep(long millis) throws IOException {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while backing off between retries", ex);
    }
  }
}
