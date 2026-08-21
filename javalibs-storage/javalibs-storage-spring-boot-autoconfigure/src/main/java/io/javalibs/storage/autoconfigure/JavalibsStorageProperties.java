package io.javalibs.storage.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration properties for the javalibs storage module ({@code javalibs.storage.*}). */
@ConfigurationProperties(prefix = "javalibs.storage")
public class JavalibsStorageProperties {

    /** Master switch; defaults to false because the feature requires a running MinIO. */
    private boolean enabled = false;

    /** Internal endpoint for server-side operations, e.g. http://minio:9000. */
    private String endpoint;

    /**
     * Public origin the browser calls for presigned URLs, e.g. http://localhost:5173.
     * Falls back to {@link #endpoint} when unset.
     */
    private String externalEndpoint;

    /** Access key of the storage credentials. */
    private String accessKey;

    /** Secret key of the storage credentials. */
    private String secretKey;

    /** Bucket holding every object; created on startup when ensure-bucket is true. */
    private String bucket;

    /** Create the bucket at startup when missing (opens a connection at boot). */
    private boolean ensureBucket = false;

    /** Validity of presigned PUT URLs. */
    private Duration presignPutExpiry = Duration.ofMinutes(10);

    /** Validity of presigned GET URLs. */
    private Duration presignGetExpiry = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getExternalEndpoint() {
        return externalEndpoint;
    }

    public void setExternalEndpoint(String externalEndpoint) {
        this.externalEndpoint = externalEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isEnsureBucket() {
        return ensureBucket;
    }

    public void setEnsureBucket(boolean ensureBucket) {
        this.ensureBucket = ensureBucket;
    }

    public Duration getPresignPutExpiry() {
        return presignPutExpiry;
    }

    public void setPresignPutExpiry(Duration presignPutExpiry) {
        this.presignPutExpiry = presignPutExpiry;
    }

    public Duration getPresignGetExpiry() {
        return presignGetExpiry;
    }

    public void setPresignGetExpiry(Duration presignGetExpiry) {
        this.presignGetExpiry = presignGetExpiry;
    }
}
