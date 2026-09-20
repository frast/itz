package de.itz.domain.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UploadedFileTest {
    @Test
    void acceptsNonnegativeLongSizesAndTypedIdentity() {
        UUID id = UUID.randomUUID();
        for (long size : new long[]{0, Long.MAX_VALUE}) {
            UploadedFile file = new UploadedFile(id, "file.txt", "text/plain", size);
            assertEquals(id, file.id());
            assertEquals(size, file.size());
        }
    }

    @Test
    void rejectsUnknownOrNegativeStoredSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadedFile(UUID.randomUUID(), "file.txt", "text/plain", -1));
    }

    @Test
    @SuppressWarnings({"NullAway", "null"})
    void requiresIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadedFile(null, "file.txt", "text/plain", 0));
    }
}
