package de.itz.domain.file;

import java.util.UUID;

public record UploadedFile(UUID id, FileName filename, ContentType contentType, long size) {
    public UploadedFile(UUID id, String filename, String contentType, long size) {
        this(id, new FileName(filename), new ContentType(contentType), size);
    }

    public UploadedFile(UUID id, FileName filename, String contentType, long size) {
        this(id, filename, new ContentType(contentType), size);
    }

    public UploadedFile {
        if (id == null || filename == null || contentType == null
                || size < 0) {
            throw new IllegalArgumentException("Invalid uploaded file metadata");
        }
    }
}
