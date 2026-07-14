package io.javalibs.datahub.spring.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Unit tests for {@link RetryingClientHttpRequestInterceptor} using a mocked
 * {@link ClientHttpRequestExecution}.
 */
@ExtendWith(MockitoExtension.class)
class RetryingClientHttpRequestInterceptorTest {

  private static final byte[] BODY = new byte[0];

  @Mock
  private HttpRequest request;

  @Mock
  private ClientHttpRequestExecution execution;

  private RetryingClientHttpRequestInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new RetryingClientHttpRequestInterceptor(3, Duration.ofMillis(1));
    lenient().when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
  }

  private ClientHttpResponse response(int status) throws IOException {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
    return response;
  }

  @Test
  void retriesIoExceptionThenSucceeds() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    ClientHttpResponse ok = response(200);
    when(execution.execute(any(), any()))
        .thenThrow(new IOException("connection reset"))
        .thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, BODY, execution);

    assertThat(result).isSameAs(ok);
    verify(execution, times(2)).execute(any(), any());
  }

  @Test
  void throwsLastIoExceptionWhenAttemptsExhausted() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(execution.execute(any(), any())).thenThrow(new IOException("connection reset"));

    assertThatThrownBy(() -> interceptor.intercept(request, BODY, execution))
        .isInstanceOf(IOException.class)
        .hasMessage("connection reset");
    verify(execution, times(3)).execute(any(), any());
  }

  @Test
  void retriesServerErrorThenSucceedsAndClosesFailedResponse() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    ClientHttpResponse serverError = response(500);
    ClientHttpResponse ok = response(200);
    when(execution.execute(any(), any())).thenReturn(serverError, ok);

    ClientHttpResponse result = interceptor.intercept(request, BODY, execution);

    assertThat(result).isSameAs(ok);
    verify(execution, times(2)).execute(any(), any());
    verify(serverError).close();
  }

  @Test
  void returnsLastServerErrorResponseWhenAttemptsExhausted() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    ClientHttpResponse serverError = response(503);
    when(execution.execute(any(), any())).thenReturn(serverError);

    ClientHttpResponse result = interceptor.intercept(request, BODY, execution);

    assertThat(result).isSameAs(serverError);
    verify(execution, times(3)).execute(any(), any());
  }

  @Test
  void doesNotRetryPostByDefault() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(execution.execute(any(), any())).thenThrow(new IOException("connection reset"));

    assertThatThrownBy(() -> interceptor.intercept(request, BODY, execution))
        .isInstanceOf(IOException.class);
    verify(execution, times(1)).execute(any(), any());
  }

  @Test
  void passesSuccessfulPostThroughWithoutInspection() throws IOException {
    when(request.getMethod()).thenReturn(HttpMethod.POST);
    // Plain mock: the interceptor must return the response without even looking at it.
    ClientHttpResponse ok = mock(ClientHttpResponse.class);
    when(execution.execute(any(), any())).thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, BODY, execution);

    assertThat(result).isSameAs(ok);
    verify(execution, times(1)).execute(any(), any());
    verify(ok, never()).close();
  }

  @Test
  void retriesPostWhenRetryAllMethodsIsEnabled() throws IOException {
    interceptor = new RetryingClientHttpRequestInterceptor(3, Duration.ofMillis(1), true);
    when(request.getMethod()).thenReturn(HttpMethod.POST);
    ClientHttpResponse ok = response(200);
    when(execution.execute(any(), any()))
        .thenThrow(new IOException("connection reset"))
        .thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, BODY, execution);

    assertThat(result).isSameAs(ok);
    verify(execution, times(2)).execute(any(), any());
  }

  @Test
  void rejectsInvalidConstructorArguments() {
    assertThatThrownBy(() -> new RetryingClientHttpRequestInterceptor(0, Duration.ofMillis(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetryingClientHttpRequestInterceptor(3, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetryingClientHttpRequestInterceptor(3, Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
