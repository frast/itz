package de.itz.adapter.primary.rest;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
    @Override
    @SuppressWarnings("null") // JDT cannot derive nullness from the unannotated JAX-RS contract.
    public Response toResponse(UnauthorizedException exception) {
        ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Authentication is required");
        return buildResponse(Response.Status.UNAUTHORIZED, MediaType.APPLICATION_JSON_TYPE, error);
    }

    Response buildResponse(Response.Status status, MediaType mediaType, ErrorResponse error) {
        return Response.status(status).type(mediaType).entity(error).build();
    }
}
