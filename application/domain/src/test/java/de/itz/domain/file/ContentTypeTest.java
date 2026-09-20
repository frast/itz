package de.itz.domain.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ContentTypeTest {
    @Test
    void acceptsParametersAndLengthBoundaryWithoutChangingValue() {
        for (String value : List.of("application/octet-stream", "text/plain; charset=UTF-8",
                "application/vnd.example+json", "Text/Plain; Charset=\"UTF-8\"", "application/x-custom",
                "text/plain; note=\"a; b=\\\"c\\\"\"", "text/plain; note=\"\"", "text/plain\t;\tcharset=UTF-8",
                "text/plain;", "text/plain;; charset=UTF-8", "text/plain; note=\"café\"",
                "text/plain; note=" + "a".repeat(495), "text/plain; note=\"" + "é".repeat(493) + "\"")) {
            assertEquals(value, new ContentType(value).value());
        }
    }

    @Test
    void rejectsMalformedMediaTypesAndParameters() {
        for (String value : List.of("text", "/plain", "text/", "text/plain/extra", "text /plain",
                "text/ plain", "text/plain,application/json", "*/*", "text/*", "*/plain",
                "text/plain; charset", "text/plain; charset=", "text/plain; =UTF-8",
                "text/plain; charset =UTF-8", "text/plain; charset= UTF-8", "text/plain; note=a b",
                "text/plain; note=\"unfinished", "text/plain; note=\"a\"junk", "text/plain; note=\"a\\",
                "text/plain\r\nX-Test: bad", "text/plain; note=\"a\u0000b\"", "text/plain; note=\"a\u007Fb\"",
                "text/plain; note=é", "text/plain; note=\"📄\"", "tèxt/plain")) {
            assertThrows(InvalidContentTypeException.class, () -> new ContentType(value), value);
            assertThrows(InvalidContentTypeException.class,
                    () -> new UploadedFile(UUID.randomUUID(), "test.txt", value, 0), value);
        }
    }

    @Test
    void rejectsBlankOversizeAndMalformedUnicode() {
        for (String value : List.of("", " \t", "text/plain; note=" + "a".repeat(496),
                "text/plain; note=" + "📄".repeat(496), "text/plain; note=\uD800", "text/plain; note=\uDC00")) {
            assertThrows(InvalidContentTypeException.class, () -> new ContentType(value));
            assertThrows(InvalidContentTypeException.class,
                    () -> new UploadedFile(UUID.randomUUID(), "test.txt", value, 0));
        }
    }
}
