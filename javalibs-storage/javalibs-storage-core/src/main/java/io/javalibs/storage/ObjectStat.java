package io.javalibs.storage;

/** Metadata of a stored object as reported by the store. */
public record ObjectStat(long size, String contentType) {
}
