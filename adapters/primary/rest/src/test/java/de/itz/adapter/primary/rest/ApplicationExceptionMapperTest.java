package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

class ApplicationExceptionMapperTest {
    @Test
    void logsUnexpectedFailuresOnceWithoutCauseDetails() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger
                .getLogger(ApplicationExceptionMapper.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(@SuppressWarnings("null") LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            try (Response ignored = new ApplicationExceptionMapper()
                    .toResponse(new IllegalStateException("SECRET_MARKER"))) {
                assertEquals(1, records.size());
                assertNull(records.get(0).getThrown());
                assertFalse(records.get(0).getMessage().contains("SECRET_MARKER"));
            }
            records.clear();
            try (Response ignored = new ApplicationExceptionMapper()
                    .toResponse(new InvalidUploadException("SECRET_MARKER"))) {
                assertEquals(0, records.size());
            }
        } finally {
            logger.removeHandler(handler);
        }
    }

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
        assertResponse(new InvalidFileNameException(), 400, "INVALID_FILE_NAME",
                "The file name must be valid Unicode, nonblank and contain at most 255 characters");
    }

    @Test
    void mapsInvalidContentType() {
        assertResponse(new InvalidContentTypeException(), 400, "INVALID_CONTENT_TYPE",
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
        try (Response response = new ApplicationExceptionMapper().toResponse(exception)) {
            assertEquals(status, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertEquals(code, error.getCode());
            assertEquals(message, error.getMessage());
        }
    }
}
