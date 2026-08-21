package io.javalibs.storage.autoconfigure;

import io.javalibs.storage.MinioObjectStorage;
import io.javalibs.storage.ObjectStorage;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Creates a {@link MinioObjectStorage} from {@code javalibs.storage.*}. Opt-in via
 * {@code javalibs.storage.enabled=true}; backs off when the application defines its
 * own {@link ObjectStorage} bean.
 */
@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "javalibs.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JavalibsStorageProperties.class)
public class JavalibsStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage javalibsObjectStorage(JavalibsStorageProperties p) {
        String external = (p.getExternalEndpoint() == null || p.getExternalEndpoint().isBlank())
                ? p.getEndpoint() : p.getExternalEndpoint();
        MinioObjectStorage storage = new MinioObjectStorage(p.getEndpoint(), external, p.getRegion(),
                p.getAccessKey(), p.getSecretKey(), p.getBucket(),
                p.getPresignPutExpiry(), p.getPresignGetExpiry());
        if (p.isEnsureBucket()) {
            storage.ensureBucket();
        }
        return storage;
    }
}
