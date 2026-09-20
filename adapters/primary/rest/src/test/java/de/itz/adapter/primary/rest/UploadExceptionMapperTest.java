package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.file.FileTooLargeException;
import de.itz.application.file.FileUploadException;
import de.itz.application.file.FileUploadRejectedException;
import de.itz.domain.file.InvalidContentTypeException;
import de.itz.domain.file.InvalidFileNameException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class UploadExceptionMapperTest {
    @Test
    void mapsRejectedFile() {
        assertResponse(new FileUploadRejectedException(),
                422, "FILE_INFECTED", "The uploaded file was rejected by the virus scanner");
    }

    @Test
    void mapsInvalidUpload() {
        assertResponse(new InvalidUploadException("Internal detail"), 400, "INVALID_UPLOAD",
                "The upload request is invalid");
    }

    @Test
    void mapsInvalidFileName() {
        assertResponse(new InvalidFileNameException(), 400, "INVALID_UPLOAD",
                "The file name must be valid Unicode, nonblank and contain at most 255 characters");
    }

    @Test
    void mapsInvalidContentType() {
        assertResponse(new InvalidContentTypeException(), 400, "INVALID_UPLOAD",
                "The content type must be valid Unicode, nonblank and contain at most 512 characters");
    }

    @Test
    void mapsOversizedFile() {
        assertResponse(new FileTooLargeException(), 413, "FILE_TOO_LARGE", "The uploaded file is too large");
    }

    @Test
    void mapsStorageFailure() {
        assertResponse(new FileUploadException(new IllegalStateException("Internal detail")),
                500, "UPLOAD_FAILED", "The file could not be stored");
    }

    @Test
    void mapsUnknownFailure() {
        assertResponse(new IllegalStateException("Internal detail"), 500, "REQUEST_FAILED",
                "The request could not be completed");
    }

    @Test
    void mapsNullFailure() {
        assertResponse(null, 500, "REQUEST_FAILED", "The request could not be completed");
    }

    private void assertResponse(@Nullable RuntimeException exception, int status, String code, String message) {
        try (Response response = new UploadExceptionMapper().toResponse(exception)) {
            assertEquals(status, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertEquals(code, error.getCode());
            assertEquals(message, error.getMessage());
        }
    }
}
