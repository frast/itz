package de.itz.adapter.primary.rest;

import de.itz.application.security.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    @SuppressWarnings("null") // JDT cannot derive nullness from the unannotated JAX-RS contract.
    public Response toResponse(ForbiddenException exception) {
        return ApiErrorResponses.create(Response.Status.FORBIDDEN.getStatusCode(), ApiErrorCode.FORBIDDEN);
    }
}
