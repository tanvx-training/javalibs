package io.javalibs.logging.logback;

import java.util.LinkedHashMap;
import java.util.Map;

import io.javalibs.logging.LogHost;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavalibsLogFormatSettingsTest {

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    @Test
    void fallsBackToApplicationNameAndMachineHost() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("spring.application.name", "auth-service")));

        assertThat(settings.service()).isEqualTo("auth-service");
        assertThat(settings.host()).isEqualTo(LogHost.current());
    }

    @Test
    void explicitServiceAndHostWinOverFallbacks() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of(
                "spring.application.name", "auth-service",
                "javalibs.logging.service", "auth",
                "javalibs.logging.host", "server-01")));

        assertThat(settings.service()).isEqualTo("auth");
        assertThat(settings.host()).isEqualTo("server-01");
    }

    @Test
    void environmentAndVersionBecomeMetadataEntries() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of(
                "javalibs.logging.environment", "production",
                "javalibs.logging.version", "1.2.3",
                "javalibs.logging.metadata.region", "ap-southeast-1")));

        assertThat(settings.metadata())
                .containsEntry("env", "production")
                .containsEntry("version", "1.2.3")
                .containsEntry("region", "ap-southeast-1");
    }

    @Test
    void bindsStaticTagsAsAList() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.tags", "auth,login")));

        assertThat(settings.tags()).containsExactly("auth", "login");
    }

    @Test
    void appliesMaskingDefaultsAndExtraKeys() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.masking.keys", "national-id")));

        assertThat(settings.maskingEnabled()).isTrue();
        assertThat(settings.mask()).isEqualTo("********");
        assertThat(settings.sensitiveKeys().isSensitive("nationalId")).isTrue();
        assertThat(settings.sensitiveKeys().isSensitive("password")).isTrue();
    }

    @Test
    void blankMaskingValueFallsBackToTheDefaultMask() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.masking.value", "")));

        assertThat(settings.mask()).isEqualTo("********");
    }

    @Test
    void disablingMaskingYieldsAPolicyThatMatchesNothing() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.masking.enabled", "false")));

        assertThat(settings.maskingEnabled()).isFalse();
        assertThat(settings.sensitiveKeys().isSensitive("password")).isFalse();
    }

    @Test
    void stacktraceDefaultsAreOnAndBounded() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of()));

        assertThat(settings.stacktraceEnabled()).isTrue();
        assertThat(settings.stacktraceMaxLength()).isEqualTo(4096);
    }

    @Test
    void metadataPreservesInsertionOrderOfMetadataKeysThenEnvThenVersion() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("javalibs.logging.metadata.region", "ap-southeast-1");
        properties.put("javalibs.logging.environment", "production");
        properties.put("javalibs.logging.version", "1.2.3");

        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(properties));

        assertThat(settings.metadata().keySet()).containsExactly("region", "env", "version");
    }

    @Test
    void negativeStacktraceMaxLengthFallsBackToTheDefault() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.stacktrace.max-length", "-1")));

        assertThat(settings.stacktraceMaxLength()).isEqualTo(4096);
    }

    @Test
    void stacktraceMaxLengthIsReachableByItsRelaxedCamelCaseName() {
        // tags/metadata/masking.keys above are all bound through Binder, which
        // accepts max-length, maxLength, and MAX_LENGTH interchangeably. This
        // property used to be read with Environment#getProperty(), which only
        // matches the exact key it names -- so an application YAML written as
        // "maxLength:" (which Spring Boot itself accepts everywhere else, and
        // which LoggingProperties#stacktrace().maxLength() also binds via
        // @ConfigurationProperties relaxed binding) was silently ignored here.
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.stacktrace.maxLength", "512")));

        assertThat(settings.stacktraceMaxLength()).isEqualTo(512);
    }

    @Test
    void metadataIsUnmodifiable() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.environment", "production")));

        assertThatThrownBy(() -> settings.metadata().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
