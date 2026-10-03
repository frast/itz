package de.itz.adapter.primary.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HttpExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(@SuppressWarnings("null") WebApplicationException exception) {
        Response original = exception.getResponse();
        int status = original.getStatus();
        if (status < 400) {
            return original;
        }
        Response response = ApiErrorResponses.response(status, ApiErrorResponses.generic(status));
        ApiErrorResponses.protocolHeaders(status, original.getHeaders(), response.getHeaders());
        return response;
    }
}
