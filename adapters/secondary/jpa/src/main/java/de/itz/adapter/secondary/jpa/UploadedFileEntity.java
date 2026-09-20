package de.itz.adapter.secondary.jpa;

import java.util.UUID;

import de.itz.domain.file.UploadedFile;
import de.itz.domain.file.FileName;
import de.itz.domain.file.ContentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "uploaded_file")
public class UploadedFileEntity {
    @Id
    @Column(length = 36, nullable = false)
    private String id = "";

    @Column(name = "original_filename", nullable = false, length = FileName.MAX_LENGTH, columnDefinition = "VARCHAR2(255 CHAR)")
    private String filename = "";

    @Column(name = "content_type", nullable = false, length = ContentType.MAX_LENGTH, columnDefinition = "VARCHAR2(512 CHAR)")
    private String contentType = "";

    @Column(name = "byte_size", nullable = false)
    private long size;

    @Column(name = "storage_key", length = 40, nullable = false, unique = true)
    private String storageKey = "";

    protected UploadedFileEntity() {
    }

    UploadedFileEntity(UploadedFile file, String storageKey) {
        this.id = file.id().toString();
        this.filename = file.filename().value();
        this.contentType = file.contentType().value();
        this.size = file.size();
        this.storageKey = storageKey;
    }

    UploadedFile toDomain() {
        return new UploadedFile(UUID.fromString(id), filename, contentType, size);
    }

    String storageKey() {
        return storageKey;
    }
}
