package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jboss.weld.junit5.auto.ActivateScopes;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.adapter.primary.rest.generated.model.FileResponse;
import de.itz.application.file.FileContent;
import de.itz.application.file.UploadFileUseCase;
import de.itz.domain.file.InvalidContentTypeException;
import de.itz.domain.file.InvalidFileNameException;
import de.itz.domain.file.UploadedFile;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@EnableAutoWeld
@ActivateScopes(RequestScoped.class)
@AddBeanClasses({FilesResource.class, FilesResourceTest.AcceptingUpload.class})
class FilesResourceTest {
    @Test
    void rejectsWildcardContentTypeBeforeOpeningContent() {
        @Nullable
        FilesResource resource = CDI.current().select(FilesResource.class).get();
        InvalidContentTypeException failure = assertThrows(InvalidContentTypeException.class,
                () -> resource.uploadFile(part(Optional.of("test.txt"), false, new MediaType("text", "*"))));
        try (Response response = new UploadExceptionMapper().toResponse(failure)) {
            assertEquals(400, response.getStatus());
            assertEquals("INVALID_UPLOAD", ((ErrorResponse) response.getEntity()).getCode());
        }
    }

    @Test
    void validatesContentTypeIncludingParametersBeforeOpeningContent() {
        @Nullable
        FilesResource resource = CDI.current().select(FilesResource.class).get();
        int overhead = new MediaType("text", "plain", Map.of("note", "a")).toString().length() - 1;
        MediaType boundary = new MediaType("text", "plain", Map.of("note", "a".repeat(512 - overhead)));
        try (Response response = resource.uploadFile(part(Optional.of("test.txt"), true, boundary))) {
            assertEquals(201, response.getStatus());
            assertEquals(boundary.toString(), ((FileResponse) response.getEntity()).getContentType());
        }
        MediaType oversized = new MediaType("text", "plain", Map.of("note", "a".repeat(513 - overhead)));
        InvalidContentTypeException failure = assertThrows(InvalidContentTypeException.class,
                () -> resource.uploadFile(part(Optional.of("test.txt"), false, oversized)));
        try (Response response = new UploadExceptionMapper().toResponse(failure)) {
            assertEquals(400, response.getStatus());
            assertEquals("INVALID_UPLOAD", ((ErrorResponse) response.getEntity()).getCode());
        }
    }

    @Test
    void rejectsInvalidFilenameBeforeOpeningContentAndMapsToBadRequest() {
        @Nullable
        FilesResource resource = CDI.current().select(FilesResource.class).get();
        for (String filename : List.of("a".repeat(256), "📄".repeat(256), "", "   ", "../report.txt",
                "C:\\report.txt", "file:stream", "file\r\n.txt", "file\u0000.txt", "file\u202Etxt.exe")) {
            EntityPart part = part(Optional.of(filename), false);
            InvalidFileNameException failure = assertThrows(InvalidFileNameException.class,
                    () -> resource.uploadFile(part));
            try (Response response = new UploadExceptionMapper().toResponse(failure)) {
                assertEquals(400, response.getStatus());
                assertEquals("INVALID_UPLOAD", ((ErrorResponse) response.getEntity()).getCode());
            }
        }
    }

    @Test
    void preservesBoundaryNamesAndFallbackInResponse() {
        @Nullable
        FilesResource resource = CDI.current().select(FilesResource.class).get();
        for (Optional<String> filename : List.of(Optional.of("a".repeat(255)), Optional.of("📄".repeat(255)),
                Optional.<String>empty())) {
            try (Response response = resource.uploadFile(part(filename, true))) {
                assertEquals(201, response.getStatus());
                assertEquals(filename.orElse("upload.bin"), ((FileResponse) response.getEntity()).getFilename());
            }
        }
    }

    private EntityPart part(Optional<String> filename, boolean readable) {
        return part(filename, readable, MediaType.TEXT_PLAIN_TYPE);
    }

    private EntityPart part(Optional<String> filename, boolean readable, MediaType contentType) {
        return (EntityPart) Proxy.newProxyInstance(EntityPart.class.getClassLoader(),
                new Class<?>[]{EntityPart.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFileName" -> filename;
                    case "getMediaType" -> contentType;
                    case "getContent" -> {
                        if (!readable) {
                            throw new AssertionError("Invalid filename must be rejected before opening content");
                        }
                        yield new ByteArrayInputStream(new byte[0]);
                    }
                    default -> throw new AssertionError("Unexpected multipart operation");
                });
    }

    @Dependent
    public static class AcceptingUpload implements UploadFileUseCase {
        @Override
        public UploadedFile execute(FileContent content) {
            return new UploadedFile(UUID.randomUUID(), content.filename(), content.contentType(), 0);
        }
    }
}
