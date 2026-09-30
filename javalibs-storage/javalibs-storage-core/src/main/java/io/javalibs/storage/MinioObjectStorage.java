package io.javalibs.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * MinIO-backed {@link ObjectStorage} holding two clients: one for server-side
 * operations against the internal endpoint, and one used exclusively to sign
 * presigned URLs against the external (browser-facing) endpoint — SigV4 binds the
 * signature to the host the browser will actually call.
 *
 * <p><b>Region is mandatory and must be set explicitly.</b> When a {@code
 * MinioClient} is built without a region, the SDK resolves it lazily on first
 * use of an operation that needs to sign a request — including {@code
 * getPresignedObjectUrl} — by issuing a real {@code GetBucketLocation} HTTP call
 * against <i>that client's own endpoint</i>. For {@code signer}, that endpoint is
 * {@code externalEndpoint}: the browser-facing host, which is frequently
 * unreachable from wherever this code actually runs (a backend container calling
 * out to a host-only address, for instance) — so leaving the region unset makes
 * every {@code presignPut}/{@code presignGet} call fail with a network error the
 * first time it runs, even though presigning is otherwise a pure local
 * computation. Passing a fixed {@code region} up front removes that lookup
 * entirely: {@code signer} never opens a connection.
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
     * @param region MinIO/S3 region both clients sign with; required so presigning never has to
     *               resolve it over the network (MinIO's own default deployment region is
     *               {@code us-east-1})
     * @param accessKey MinIO access key
     * @param secretKey MinIO secret key
     * @param bucket bucket name used for every operation
     * @param putExpiry expiry applied to presigned PUT URLs
     * @param getExpiry expiry applied to presigned GET URLs
     */
    public MinioObjectStorage(String endpoint, String externalEndpoint, String region, String accessKey,
                               String secretKey, String bucket, Duration putExpiry, Duration getExpiry) {
        this.client = MinioClient.builder().endpoint(endpoint).region(region)
                .credentials(accessKey, secretKey).build();
        this.signer = MinioClient.builder().endpoint(externalEndpoint).region(region)
                .credentials(accessKey, secretKey).build();
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
    public Stream<ObjectInfo> list(String prefix) {
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix).recursive(true).build());
            return StreamSupport.stream(results.spliterator(), false)
                    .map(r -> unwrap(r, prefix))
                    .filter(item -> !item.isDir())
                    .map(item -> new ObjectInfo(item.objectName(), item.size(), item.lastModified().toInstant()));
        } catch (Exception e) {
            throw new StorageException("list failed for prefix " + prefix, e);
        }
    }

    private static Item unwrap(Result<Item> result, String prefix) {
        try {
            return result.get();
        } catch (Exception e) {
            throw new StorageException("list failed for prefix " + prefix, e);
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
