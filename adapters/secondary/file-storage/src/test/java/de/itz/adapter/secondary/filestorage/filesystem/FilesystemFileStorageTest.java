package de.itz.adapter.secondary.filestorage.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.itz.adapter.secondary.filestorage.jpa.FileMetadataStorageException;
import de.itz.adapter.secondary.filestorage.jpa.JpaFileMetadataStore;
import de.itz.application.file.FileContent;
import de.itz.application.file.FileTooLargeException;
import de.itz.application.file.FileUploadException;
import de.itz.application.file.FileUploadRejectedException;
import de.itz.domain.file.UploadedFile;
import jakarta.transaction.RollbackException;

class FilesystemFileStorageTest {
    @Test
    void storesMetadataAndInspectedBytesUnderId(@TempDir Path directory) throws Exception {
        List<UploadedFile> records = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        JpaFileMetadataStore metadata = new StubMetadataStore() {
            @Override
            public void save(UploadedFile file, String key) {
                calls.add("save");
                records.add(file);
                assertEquals(file.id() + ".bin", key);
                assertEquals(40, key.length());
                assertTrue(Files.exists(directory.resolve(key)));
            }
        };
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, path -> {
            calls.add("scan");
            assertEquals("hello", Files.readString(path));
        }, metadata);

        UploadedFile result = storage.store(new FileContent("report.txt", "text/plain",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));

        assertEquals(List.of("scan", "save"), calls);
        assertEquals(List.of(result), records);
        assertEquals("report.txt", result.filename().value());
        assertEquals("text/plain", result.contentType().value());
        assertEquals(5, result.size());
        assertEquals("hello", Files.readString(directory.resolve(result.id() + ".bin")));
        assertEquals(1, fileCount(directory));
    }

    @Test
    void rejectsMalwareAndCleansQuarantine(@TempDir Path directory) throws Exception {
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, new MockVirusScanner(), unusedStore());
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        assertThrows(FileUploadRejectedException.class, () -> storage.store(content(eicar)));
        assertEquals(0, fileCount(directory));
    }

    @Test
    void failsClosedWhenScannerIsUnavailable(@TempDir Path directory) throws Exception {
        IOException failure = new IOException("Unavailable");
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, path -> {
            throw failure;
        }, unusedStore());
        assertSame(failure, assertThrows(FileUploadException.class, () -> storage.store(content("hello"))).getCause());
        assertEquals(0, fileCount(directory));
    }

    @Test
    void deletesFinalContentOnlyForConfirmedRollback(@TempDir Path directory) throws Exception {
        for (boolean confirmed : List.of(true, false)) {
            Path root = directory.resolve(Boolean.toString(confirmed));
            JpaFileMetadataStore metadata = new StubMetadataStore() {
                @Override
                public void save(UploadedFile file, String key) {
                    throw new FileMetadataStorageException(new RollbackException(), confirmed);
                }
            };
            FilesystemFileStorage storage = new FilesystemFileStorage(root, new MockVirusScanner(), metadata);
            assertThrows(FileUploadException.class, () -> storage.store(content("hello")));
            assertEquals(confirmed ? 0 : 1, fileCount(root));
            try (var files = Files.list(root)) {
                assertTrue(files.allMatch(path -> path.toString().endsWith(".bin")));
            }
        }
    }

    @Test
    void retainsContentForUnexpectedMetadataFailure(@TempDir Path directory) throws Exception {
        JpaFileMetadataStore metadata = new StubMetadataStore() {
            @Override
            public void save(UploadedFile file, String key) {
                throw new IllegalStateException();
            }
        };
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, new MockVirusScanner(), metadata);
        assertThrows(FileUploadException.class, () -> storage.store(content("hello")));
        assertEquals(1, fileCount(directory));
    }

    @Test
    void enforcesLimitWhileReadingAndAcceptsBoundary(@TempDir Path directory) throws Exception {
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, path -> {
        }, new StubMetadataStore() {
            @Override
            public void save(UploadedFile file, String key) {
            }
        });
        byte[] bytes = new byte[(int) FilesystemFileStorage.MAX_SIZE + 1];
        assertThrows(FileTooLargeException.class, () -> storage.store(new FileContent("large.bin",
                "application/octet-stream", new ByteArrayInputStream(bytes))));
        assertEquals(0, fileCount(directory));
        UploadedFile result = storage.store(new FileContent("boundary.bin", "application/octet-stream",
                new ByteArrayInputStream(bytes, 0, (int) FilesystemFileStorage.MAX_SIZE)));
        assertEquals(FilesystemFileStorage.MAX_SIZE, result.size());
    }

    @Test
    void supportsEmptyContent(@TempDir Path directory) {
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, new MockVirusScanner(),
                new StubMetadataStore() {
                    @Override
                    public void save(UploadedFile file, String key) {
                    }
                });
        assertEquals(0, storage.store(content("")).size());
    }

    @Test
    void cleansPartialUploadAfterReadFailure(@TempDir Path directory) throws Exception {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Read failed");
            }
        };
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, new MockVirusScanner(), unusedStore());
        assertThrows(FileUploadException.class, () -> storage.store(
                new FileContent("broken.bin", "application/octet-stream", input)));
        assertEquals(0, fileCount(directory));
    }

    @Test
    void cleanupFailureDoesNotReplaceRejection(@TempDir Path directory) throws Exception {
        FilesystemFileStorage storage = new FilesystemFileStorage(directory, path -> {
            Files.delete(path);
            Files.createDirectory(path);
            Files.writeString(path.resolve("obstruction"), "test");
            throw new VirusDetectedException();
        }, unusedStore());
        assertThrows(FileUploadRejectedException.class, () -> storage.store(content("hello")));
        assertEquals(1, fileCount(directory));
    }

    private JpaFileMetadataStore unusedStore() {
        return new StubMetadataStore() {
            @Override
            public void save(UploadedFile file, String key) {
                throw new AssertionError("Metadata must not be written");
            }
        };
    }

    private static class StubMetadataStore extends JpaFileMetadataStore {
        private StubMetadataStore() {
            super(unusedResource(jakarta.persistence.EntityManager.class),
                    unusedResource(jakarta.transaction.UserTransaction.class));
        }

        private static <T> T unusedResource(Class<T> type) {
            return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, (proxy, method, arguments) -> {
                        throw new AssertionError("Persistence resource must not be called");
                    }));
        }
    }

    private FileContent content(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new FileContent("upload.txt", "text/plain", new ByteArrayInputStream(bytes));
    }

    private long fileCount(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }
}
