package de.itz.adapter.secondary.jpa;

public class FileMetadataStorageException extends RuntimeException {
    private final boolean rollbackConfirmed;

    public FileMetadataStorageException(Throwable cause, boolean rollbackConfirmed) {
        super("File metadata could not be stored", cause);
        this.rollbackConfirmed = rollbackConfirmed;
    }

    public boolean rollbackConfirmed() {
        return rollbackConfirmed;
    }
}
