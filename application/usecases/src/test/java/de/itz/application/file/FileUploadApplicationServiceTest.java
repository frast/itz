package de.itz.application.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.itz.application.security.CurrentUser;
import de.itz.domain.file.UploadedFile;

class FileUploadApplicationServiceTest {
    @Test
    void authenticatesBeforeDelegatingAndReturnsStoredMetadata() {
        List<String> calls = new ArrayList<>();
        FileContent content = content();
        UploadedFile stored = new UploadedFile(UUID.randomUUID(), "upload.txt", "text/plain", 0);
        FileUploadApplicationService service = new FileUploadApplicationService(() -> {
            calls.add("authenticate");
            return new CurrentUser("user", Set.of());
        }, input -> {
            calls.add("store");
            assertSame(content, input);
            return stored;
        });

        assertSame(stored, service.execute(content));
        assertEquals(List.of("authenticate", "store"), calls);
    }

    @Test
    void doesNotStoreWhenAuthenticationFails() {
        IllegalStateException failure = new IllegalStateException("Unauthenticated");
        FileUploadApplicationService service = new FileUploadApplicationService(() -> {
            throw failure;
        }, input -> {
            throw new AssertionError("Storage must not be called");
        });

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.execute(content())));
    }

    @Test
    void propagatesStorageFailures() {
        for (RuntimeException failure : List.of(new FileUploadException(new IllegalStateException()),
                new FileUploadRejectedException(new IllegalStateException()), new FileTooLargeException())) {
            FileUploadApplicationService service = new FileUploadApplicationService(
                    () -> new CurrentUser("user", Set.of()), input -> {
                        throw failure;
                    });
            assertSame(failure, assertThrows(RuntimeException.class, () -> service.execute(content())));
        }
    }

    private FileContent content() {
        return new FileContent("upload.txt", "text/plain", new ByteArrayInputStream(new byte[0]));
    }
}
