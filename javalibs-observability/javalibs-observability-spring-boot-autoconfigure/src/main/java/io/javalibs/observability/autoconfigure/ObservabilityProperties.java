package io.javalibs.observability.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import io.javalibs.observability.CorrelationId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the javalibs observability modules, bound to
 * the {@code javalibs.observability.*} namespace.
 */
@ConfigurationProperties("javalibs.observability")
public class ObservabilityProperties {

    private final Correlation correlation = new Correlation();

    private final Metrics metrics = new Metrics();

    /**
     * Returns the correlation id settings.
     *
     * @return the correlation settings, never {@code null}
     */
    public Correlation getCorrelation() {
        return this.correlation;
    }

    /**
     * Returns the metrics settings.
     *
     * @return the metrics settings, never {@code null}
     */
    public Metrics getMetrics() {
        return this.metrics;
    }

    /**
     * Settings for the correlation id servlet filter.
     */
    public static class Correlation {

        /**
         * Whether the correlation id filter is registered for servlet web
         * applications.
         */
        private boolean enabled = true;

        /**
         * HTTP header used to read and echo the correlation id.
         */
        private String header = CorrelationId.DEFAULT_HEADER;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeader() {
            return this.header;
        }

        public void setHeader(String header) {
            this.header = header;
        }
    }

    /**
     * Settings for common Micrometer metric tags.
     */
    public static class Metrics {

        /**
         * Whether common tags are applied to every MeterRegistry.
         */
        private boolean enabled = true;

        /**
         * Additional common tags applied to every meter, as key/value pairs.
         */
        private Map<String, String> commonTags = new LinkedHashMap<>();

        /**
         * Deployment environment added as the "environment" tag when set
         * (for example "dev", "staging", "prod").
         */
        private String environment;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getCommonTags() {
            return this.commonTags;
        }

        public void setCommonTags(Map<String, String> commonTags) {
            this.commonTags = commonTags;
        }

        public String getEnvironment() {
            return this.environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }
}
