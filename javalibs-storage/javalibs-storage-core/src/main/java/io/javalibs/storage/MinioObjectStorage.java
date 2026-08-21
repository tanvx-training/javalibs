package io.javalibs.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * MinIO-backed {@link ObjectStorage} holding two clients: one for server-side
 * operations against the internal endpoint, and one used exclusively to sign
 * presigned URLs against the external (browser-facing) endpoint — SigV4 binds the
 * signature to the host the browser will actually call.
 */
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final MinioClient signer;
    private final String bucket;
    private final Duration putExpiry;
    private final Duration getExpiry;

    /**
     * @param endpoint internal MinIO endpoint used for server-side operations (put/get/stat/delete)
     * @param externalEndpoint public-facing MinIO endpoint used only to sign presigned URLs
     * @param accessKey MinIO access key
     * @param secretKey MinIO secret key
     * @param bucket bucket name used for every operation
     * @param putExpiry expiry applied to presigned PUT URLs
     * @param getExpiry expiry applied to presigned GET URLs
     */
    public MinioObjectStorage(String endpoint, String externalEndpoint, String accessKey,
                               String secretKey, String bucket, Duration putExpiry, Duration getExpiry) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.signer = MinioClient.builder().endpoint(externalEndpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.putExpiry = putExpiry;
        this.getExpiry = getExpiry;
    }

    /** Creates the bucket when missing; safe to call on every startup. */
    public void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new StorageException("Cannot ensure bucket " + bucket, e);
        }
    }

    @Override
    public void put(String key, String contentType, long contentLength, InputStream content) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .contentType(contentType)
                    .stream(content, contentLength, -1).build());
        } catch (Exception e) {
            throw new StorageException("put failed for key " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("get failed for key " + key, e);
        }
    }

    @Override
    public Optional<ObjectStat> stat(String key) {
        try {
            StatObjectResponse s = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new ObjectStat(s.size(), s.contentType()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new StorageException("stat failed for key " + key, e);
        } catch (Exception e) {
            throw new StorageException("stat failed for key " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("delete failed for key " + key, e);
        }
    }

    @Override
    public String presignPut(String key) {
        try {
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(key)
                    .expiry((int) putExpiry.toSeconds()).build());
        } catch (Exception e) {
            throw new StorageException("presignPut failed for key " + key, e);
        }
    }

    @Override
    public String presignGet(String key, String contentDisposition) {
        try {
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key)
                    .expiry((int) getExpiry.toSeconds())
                    .extraQueryParams(Map.of("response-content-disposition", contentDisposition))
                    .build());
        } catch (Exception e) {
            throw new StorageException("presignGet failed for key " + key, e);
        }
    }
}
