package de.itz.adapter.primary.rest;

import java.util.UUID;

import org.jboss.logging.MDC;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds a bounded request identifier to the response and the EAP logging MDC. */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public final class RequestCorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {
    static final String HEADER = "X-Request-ID";
    private static final String MDC_KEY = "request.id";

    @Override
    @SuppressWarnings("null") // JDT cannot derive nullness from the unannotated JAX-RS contract.
    public void filter(ContainerRequestContext requestContext) {
        String requestId = requestContext.getHeaderString(HEADER);
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
            requestId = UUID.randomUUID().toString();
        }
        requestContext.setProperty(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        requestContext.getHeaders().putSingle(HEADER, requestId);
    }

    @Override
    @SuppressWarnings("null") // JDT cannot derive nullness from the unannotated JAX-RS contract.
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(HEADER);
        if (requestId instanceof String value) {
            responseContext.getHeaders().putSingle(HEADER, value);
        }
        MDC.remove(MDC_KEY);
    }
}
