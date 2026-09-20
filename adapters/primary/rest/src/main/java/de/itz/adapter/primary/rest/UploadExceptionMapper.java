package de.itz.adapter.primary.rest;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.file.FileTooLargeException;
import de.itz.application.file.FileUploadException;
import de.itz.application.file.FileUploadRejectedException;
import de.itz.domain.file.InvalidFileNameException;
import de.itz.domain.file.InvalidContentTypeException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UploadExceptionMapper implements ExceptionMapper<RuntimeException> {
    private static final Logger LOG = Logger.getLogger(UploadExceptionMapper.class);

    @Override
    public Response toResponse(@Nullable RuntimeException exception) {
        if (exception == null) {
            LOG.error("File upload failed with null exception");
            return response(Response.Status.INTERNAL_SERVER_ERROR, "REQUEST_FAILED",
                    "The request could not be completed");
        }
        LOG.errorf(exception, "File upload failed with %s", exception.getClass().getSimpleName());
        if (exception instanceof FileUploadRejectedException) {
            return response(Response.Status.fromStatusCode(422), "FILE_INFECTED",
                    "The uploaded file was rejected by the virus scanner");
        }
        if (exception instanceof InvalidUploadException) {
            return response(Response.Status.BAD_REQUEST, "INVALID_UPLOAD", "The upload request is invalid");
        }
        if (exception instanceof InvalidFileNameException) {
            return response(Response.Status.BAD_REQUEST, "INVALID_UPLOAD",
                    "The file name must be valid Unicode, nonblank and contain at most 255 characters");
        }
        if (exception instanceof InvalidContentTypeException) {
            return response(Response.Status.BAD_REQUEST, "INVALID_UPLOAD",
                    "The content type must be valid Unicode, nonblank and contain at most 512 characters");
        }
        if (exception instanceof FileTooLargeException) {
            return response(Response.Status.REQUEST_ENTITY_TOO_LARGE, "FILE_TOO_LARGE",
                    "The uploaded file is too large");
        }
        if (exception instanceof FileUploadException) {
            return response(Response.Status.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "The file could not be stored");
        }
        return response(Response.Status.INTERNAL_SERVER_ERROR, "REQUEST_FAILED", "The request could not be completed");
    }

    private Response response(Response.Status status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(code, message)).build();
    }
}
