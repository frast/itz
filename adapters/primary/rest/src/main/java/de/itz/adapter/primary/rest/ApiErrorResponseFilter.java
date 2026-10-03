package de.itz.adapter.primary.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.Provider;

/** Normalize errors, including entity-bearing exceptions that bypass exception mappers. */
@Provider
@Priority(Priorities.HEADER_DECORATOR + 100)
public class ApiErrorResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(@SuppressWarnings("null") ContainerRequestContext request,
            @SuppressWarnings("null") ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400) {
            return;
        }
        var body = ApiErrorResponses.sanitize(status, response.getEntity());
        var headers = new MultivaluedHashMap<String, Object>();
        ApiErrorResponses.protocolHeaders(status, response.getHeaders(), headers);
        response.getHeaders().clear();
        response.getHeaders().putAll(headers);
        response.getHeaders().putSingle("Content-Type", "application/json");
        response.getHeaders().putSingle("Cache-Control", "no-store");
        response.setEntity(request.getMethod().equals("HEAD") ? null : body);
    }
}
