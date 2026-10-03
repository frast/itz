package de.itz.adapter.primary.rest;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import de.itz.application.file.FileTooLargeException;
import de.itz.application.file.FileUploadException;
import de.itz.application.file.FileUploadRejectedException;
import de.itz.domain.file.InvalidFileNameException;
import de.itz.domain.file.InvalidContentTypeException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApplicationExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApplicationExceptionMapper.class);

    @Override
    public Response toResponse(@Nullable Exception exception) {
        if (exception == null) {
            LOG.error("Request failed with missing exception");
            return ApiErrorResponses.create(500, ApiErrorCode.REQUEST_FAILED);
        }
        if (!(exception instanceof FileUploadRejectedException || exception instanceof InvalidUploadException
                || exception instanceof InvalidFileNameException || exception instanceof InvalidContentTypeException
                || exception instanceof FileTooLargeException || exception instanceof PayloadTooLargeException)) {
            LOG.error("Request failed; consult the correlated operational event");
        }
        return switch (exception) {
            case PayloadTooLargeException tooLarge -> ApiErrorResponses.create(413, ApiErrorCode.PAYLOAD_TOO_LARGE);
            case FileUploadRejectedException rejected -> ApiErrorResponses.create(422, ApiErrorCode.FILE_INFECTED);
            case InvalidUploadException invalid -> ApiErrorResponses.create(400, ApiErrorCode.INVALID_UPLOAD);
            case InvalidFileNameException invalid -> ApiErrorResponses.create(400, ApiErrorCode.INVALID_FILE_NAME);
            case InvalidContentTypeException invalid ->
                ApiErrorResponses.create(400, ApiErrorCode.INVALID_CONTENT_TYPE);
            case FileTooLargeException tooLarge -> ApiErrorResponses.create(413, ApiErrorCode.FILE_TOO_LARGE);
            case FileUploadException failed -> ApiErrorResponses.create(500, ApiErrorCode.UPLOAD_FAILED);
            default -> ApiErrorResponses.create(500, ApiErrorCode.REQUEST_FAILED);
        };
    }
}
