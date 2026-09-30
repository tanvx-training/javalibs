package io.javalibs.storage;

import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstraction over an object store (MinIO/S3). Keys are opaque strings owned by the
 * caller; implementations must not interpret or rewrite them.
 */
public interface ObjectStorage {

    /** Stores the content under {@code key}, overwriting any existing object. */
    void put(String key, String contentType, long contentLength, InputStream content);

    /**
     * Streams the object content. The caller must close the returned stream.
     *
     * @throws StorageException when the object does not exist or the store is unreachable
     */
    InputStream get(String key);

    /** Object metadata, or {@link Optional#empty()} when no object exists under {@code key}. */
    Optional<ObjectStat> stat(String key);

    /** Deletes the object if present; deleting a missing key is a no-op. */
    void delete(String key);

    /**
     * Lists every object whose key starts with {@code prefix}, recursively (no
     * directory-delimiter grouping — a nested key like {@code "a/b/c.pdf"} is
     * returned by {@code list("a/")} just like a flat one). The returned stream is
     * lazy: pages are fetched from the store as the stream is consumed, and the
     * caller must close it (try-with-resources) once done.
     *
     * @throws StorageException when the store is unreachable or listing fails
     */
    Stream<ObjectInfo> list(String prefix);

    /**
     * Presigned PUT URL signed against the external (browser-facing) endpoint.
     * Expiry is fixed at construction time.
     */
    String presignPut(String key);

    /**
     * Presigned GET URL signed against the external endpoint, with a
     * {@code response-content-disposition} override (see {@link ContentDispositions}).
     */
    String presignGet(String key, String contentDisposition);
}
