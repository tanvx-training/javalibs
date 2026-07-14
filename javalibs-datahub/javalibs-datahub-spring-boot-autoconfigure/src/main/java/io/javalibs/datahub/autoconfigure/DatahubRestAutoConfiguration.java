package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.spring.rest.DatahubRestClients;
import io.javalibs.datahub.spring.rest.RetryingClientHttpRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration registering a pre-configured {@link RestClient.Builder} bean
 * named {@code datahubRestClientBuilder} when spring-web is on the classpath and
 * {@code javalibs.datahub.rest.enabled} is not set to {@code false}.
 *
 * <p>The builder applies the configured connect/read timeouts and a
 * {@link RetryingClientHttpRequestInterceptor} with exponential backoff. Applications
 * inject it by name and customize it further (base URL, default headers, ...) before
 * building their {@link RestClient}.</p>
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "javalibs.datahub.rest", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DatahubProperties.class)
public class DatahubRestAutoConfiguration {

  /**
   * Creates the datahub {@link RestClient.Builder} from the configured timeouts and
   * retry settings.
   *
   * @param properties the datahub configuration properties
   * @return a pre-configured builder, still open for further customization
   */
  @Bean(name = "datahubRestClientBuilder")
  @ConditionalOnMissingBean(name = "datahubRestClientBuilder")
  public RestClient.Builder datahubRestClientBuilder(DatahubProperties properties) {
    DatahubProperties.Rest rest = properties.getRest();
    RetryingClientHttpRequestInterceptor retry = new RetryingClientHttpRequestInterceptor(
        rest.getMaxRetries(), rest.getInitialBackoff());
    return DatahubRestClients.defaultBuilder(
        rest.getConnectTimeout(), rest.getReadTimeout(), retry);
  }
}
