package de.itz.application.file;

import de.itz.domain.file.UploadedFile;

public interface FileStorage {
    /**
     * Stores inspected content and metadata, returning only after persistence succeeds.
     * Rejects malicious content and fails closed when inspection is unavailable.
     * The caller retains ownership of the input stream.
     */
    UploadedFile store(FileContent content);
}
