package io.javalibs.storage;

/** Wraps any object-store failure (network, auth, missing object on read). */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
