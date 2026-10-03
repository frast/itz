package de.itz.adapter.primary.rest;

import org.jboss.resteasy.spi.ReaderException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** A RESTEasy reader failure belongs to request decoding, not application execution. */
@Provider
public class RequestBodyExceptionMapper implements ExceptionMapper<ReaderException> {
    @Override
    public Response toResponse(@SuppressWarnings("null") ReaderException exception) {
        if (exception.getCause() instanceof PayloadTooLargeException) {
            return ApiErrorResponses.create(413, ApiErrorCode.PAYLOAD_TOO_LARGE);
        }
        return ApiErrorResponses.create(400, ApiErrorCode.BAD_REQUEST);
    }
}
