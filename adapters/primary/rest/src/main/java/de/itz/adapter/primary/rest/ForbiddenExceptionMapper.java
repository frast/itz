package de.itz.adapter.primary.rest;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.security.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    public Response toResponse(ForbiddenException exception) {
        ErrorResponse error = new ErrorResponse(
                "FORBIDDEN", "The current user is not allowed to perform this operation");
        return buildResponse(Response.Status.FORBIDDEN, MediaType.APPLICATION_JSON_TYPE, error);
    }

    Response buildResponse(Response.Status status, MediaType mediaType, ErrorResponse error) {
        return Response.status(status).type(mediaType).entity(error).build();
    }
}
