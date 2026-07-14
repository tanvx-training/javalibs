package io.javalibs.datahub.spring.rest;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Static factory for {@link RestClient.Builder} instances pre-configured with the
 * javalibs resilience defaults: explicit connect/read timeouts and (optionally) the
 * {@link RetryingClientHttpRequestInterceptor}.
 *
 * <p>The builder uses a {@link JdkClientHttpRequestFactory} backed by the JDK
 * {@link HttpClient}; the connect timeout is applied to the underlying client and the
 * read timeout to the request factory. Callers can further customize the returned
 * builder (base URL, default headers, message converters, ...) before calling
 * {@code build()}.</p>
 */
public final class DatahubRestClients {

  private DatahubRestClients() {
  }

  /**
   * Creates a {@link RestClient.Builder} with the given timeouts and retry
   * interceptor.
   *
   * @param connectTimeout maximum time to establish a TCP connection
   * @param readTimeout    maximum time to wait for response data
   * @param retry          retry interceptor to register; may be {@code null} to
   *                       disable retries
   * @return a pre-configured builder, still open for further customization
   */
  public static RestClient.Builder defaultBuilder(
      Duration connectTimeout, Duration readTimeout,
      RetryingClientHttpRequestInterceptor retry) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);

    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    if (retry != null) {
      builder = builder.requestInterceptor(retry);
    }
    return builder;
  }
}
