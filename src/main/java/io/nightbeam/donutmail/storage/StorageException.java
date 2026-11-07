package io.nightbeam.donutmail.storage;

/** A storage operation failed (database unreachable, disk full...). Nothing was changed. */
public final class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
