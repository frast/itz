package de.itz.adapter.secondary.filesystem;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import de.itz.adapter.secondary.jpa.FileMetadataStorageException;
import de.itz.adapter.secondary.jpa.JpaFileMetadataStore;
import de.itz.application.file.FileContent;
import de.itz.application.file.FileStorage;
import de.itz.application.file.FileTooLargeException;
import de.itz.application.file.FileUploadException;
import de.itz.application.file.FileUploadRejectedException;
import de.itz.domain.file.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FilesystemFileStorage implements FileStorage {
    static final long MAX_SIZE = 25L * 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(FilesystemFileStorage.class.getName());
    private final Path root;
    private final VirusScanner scanner;
    private final JpaFileMetadataStore metadataStore;

    @Inject
    public FilesystemFileStorage(VirusScanner scanner, JpaFileMetadataStore metadataStore) {
        this(Path.of(System.getProperty("itz.file-storage.directory",
                System.getenv().getOrDefault("ITZ_FILE_STORAGE_DIRECTORY", "./data/files"))), scanner, metadataStore);
    }

    FilesystemFileStorage(Path root, VirusScanner scanner, JpaFileMetadataStore metadataStore) {
        this.root = root.toAbsolutePath().normalize();
        this.scanner = scanner;
        this.metadataStore = metadataStore;
    }

    @Override
    public UploadedFile store(FileContent content) {
        Path quarantine = null;
        Path target = null;
        boolean metadataAttempted = false;
        try {
            Files.createDirectories(root);
            quarantine = Files.createTempFile(root, ".upload-", ".tmp");
            long size = copyContent(content, quarantine);
            scanner.scan(quarantine);
            UUID id = UUID.randomUUID();
            String storageKey = id + ".bin";
            UploadedFile result = new UploadedFile(id, content.filename(), content.contentType(), size);
            target = root.resolve(storageKey);
            Files.move(quarantine, target, StandardCopyOption.ATOMIC_MOVE);
            metadataAttempted = true;
            metadataStore.save(result, storageKey);
            return result;
        } catch (VirusDetectedException exception) {
            throw new FileUploadRejectedException(exception);
        } catch (FileTooLargeException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (!metadataAttempted || (exception instanceof FileMetadataStorageException failure
                    && failure.rollbackConfirmed())) {
                cleanup(target);
            } else {
                LOG.log(System.Logger.Level.ERROR,
                        "File metadata commit outcome is uncertain; retained content requires reconciliation");
            }
            throw new FileUploadException(exception);
        } finally {
            cleanup(quarantine);
        }
    }

    private long copyContent(FileContent content, Path quarantine) throws IOException {
        long size = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(quarantine)) {
            int count;
            while ((count = content.content().read(buffer)) != -1) {
                size += count;
                if (size > MAX_SIZE) {
                    throw new FileTooLargeException();
                }
                output.write(buffer, 0, count);
            }
        }
        return size;
    }

    private void cleanup(@Nullable Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException | RuntimeException exception) {
                // Never log paths, filenames or provider exception messages.
                LOG.log(System.Logger.Level.WARNING, "Upload content cleanup failed; reconciliation required");
            }
        }
    }
}
