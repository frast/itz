package de.itz.application.file;

import java.io.InputStream;
import de.itz.domain.file.FileName;
import de.itz.domain.file.ContentType;

public record FileContent(FileName filename, ContentType contentType, InputStream content) {
    public FileContent(String filename, String contentType, InputStream content) {
        this(new FileName(filename), new ContentType(contentType), content);
    }

    public FileContent(FileName filename, String contentType, InputStream content) {
        this(filename, new ContentType(contentType), content);
    }

    public FileContent {
        if (filename == null || contentType == null || content == null) {
            throw new IllegalArgumentException("Invalid file content");
        }
    }
}
