package de.itz.domain.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FileNameTest {
    @Test
    void acceptsBoundaryWithoutChangingName() {
        for (String name : List.of("a", "a".repeat(255), "é".repeat(255), "📄".repeat(255), " report.txt ",
                "Bericht 2026 (final).pdf", ".gitignore", "report..txt", "日本語.txt", "تقرير.pdf", "👩‍💻.txt",
                "e\u0301".repeat(127) + "a")) {
            assertEquals(name, new FileName(name).value());
        }
    }

    @Test
    void rejectsPathsAndForbiddenCharacters() {
        for (String name : List.of(".", "..", "../report.txt", "a/b.txt", "a\\b.txt", "C:\\report.txt",
                "file:stream", "<file>", "file\".txt", "file|.txt", "file?.txt", "file*.txt",
                "file\u0000.txt", "file\r.txt", "file\n.txt", "file\t.txt", "file\u007F.txt", "file\u0085.txt",
                "file\u2028.txt", "file\u2029.txt", "file\u061C.txt", "file\u200E.txt", "file\u200F.txt",
                "file\u202A.txt", "file\u202Etxt.exe", "file\u2066.txt", "file\u2069.txt")) {
            assertThrows(InvalidFileNameException.class, () -> new FileName(name), name);
            assertThrows(InvalidFileNameException.class,
                    () -> new UploadedFile(UUID.randomUUID(), name, "text/plain", 0), name);
        }
    }

    @Test
    void rejectsBlankAndOversizeNames() {
        for (String name : List.of("", " \t\n", "a".repeat(256), "📄".repeat(256), "e\u0301".repeat(128),
                "bad\uD800", "bad\uDC00")) {
            assertThrows(InvalidFileNameException.class, () -> new FileName(name));
            assertThrows(InvalidFileNameException.class,
                    () -> new UploadedFile(UUID.randomUUID(), name, "text/plain", 0));
        }
    }
}
