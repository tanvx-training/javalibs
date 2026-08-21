package io.javalibs.storage.autoconfigure;

import io.javalibs.storage.ObjectStorage;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavalibsStorageAutoConfiguration.class));

    private static final String[] ENABLED_PROPS = {
            "javalibs.storage.enabled=true",
            "javalibs.storage.endpoint=http://localhost:9000",
            "javalibs.storage.access-key=a",
            "javalibs.storage.secret-key=s",
            "javalibs.storage.bucket=b",
    };

    @Configuration
    static class WithUserStorage {
        @Bean
        ObjectStorage userStorage() {
            return Mockito.mock(ObjectStorage.class);
        }
    }

    @Test
    void inactiveByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
    }

    @Test
    void activeWhenEnabled() {
        // ensure-bucket mặc định false → dựng bean không mở kết nối nào
        runner.withPropertyValues(ENABLED_PROPS)
                .run(context -> assertThat(context).hasSingleBean(ObjectStorage.class));
    }

    @Test
    void backsOffForUserBean() {
        runner.withPropertyValues(ENABLED_PROPS)
                .withUserConfiguration(WithUserStorage.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context).hasBean("userStorage");
                });
    }

    @Test
    void inactiveWithoutMinioOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(MinioClient.class))
                .withPropertyValues(ENABLED_PROPS)
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
    }
}
