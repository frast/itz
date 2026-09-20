package de.itz.application.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import de.itz.domain.file.InvalidFileNameException;
import de.itz.domain.file.InvalidContentTypeException;

class FileContentTest {
    @Test
    void rejectsOversizeContentTypeBeforeUseCaseExecution() {
        assertThrows(InvalidContentTypeException.class, () -> new FileContent("file.txt",
                "text/plain; note=" + "a".repeat(496), new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void rejectsInvalidNameBeforeUseCaseExecution() {
        assertThrows(InvalidFileNameException.class, () -> new FileContent("a".repeat(256),
                "text/plain", new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void carriesValidatedNameAtBoundary() {
        String name = "📄".repeat(255);
        FileContent content = new FileContent(name, "text/plain", new ByteArrayInputStream(new byte[0]));
        assertEquals(name, content.filename().value());
    }
}
