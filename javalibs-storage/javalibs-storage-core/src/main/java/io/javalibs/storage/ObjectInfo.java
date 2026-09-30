package io.javalibs.storage;

import java.time.Instant;

/** One object entry returned by {@link ObjectStorage#list(String)}. */
public record ObjectInfo(String key, long size, Instant lastModified) {
}
