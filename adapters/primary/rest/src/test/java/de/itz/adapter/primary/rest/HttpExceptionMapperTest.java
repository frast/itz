package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class HttpExceptionMapperTest {
    @Test
    void preservesStatusWithoutLeakingBodyOrHeaders() {
        for (int status : new int[]{400, 401, 403, 404, 405, 406, 413, 415, 429, 500, 503, 507}) {
            try (Response original = Response.status(status).entity("SECRET_MARKER")
                    .header("X-Debug", "SECRET_MARKER").header("Set-Cookie", "secret=SECRET_MARKER")
                    .header("Content-Encoding", "gzip").header("Content-Length", "999").build();
                    Response mapped = new HttpExceptionMapper()
                            .toResponse(new WebApplicationException("SECRET_MARKER", original))) {
                assertEquals(status, mapped.getStatus());
                assertFalse(((ErrorResponse) mapped.getEntity()).getMessage().contains("SECRET_MARKER"));
                assertNull(mapped.getHeaderString("X-Debug"));
                assertNull(mapped.getHeaderString("Set-Cookie"));
                assertNull(mapped.getHeaderString("Content-Encoding"));
                assertEquals("no-store", mapped.getHeaderString("Cache-Control"));
            }
        }
    }

    @Test
    void preservesProtocolHeadersOnly() {
        try (Response original = Response.status(405).header("Allow", "GET").header("Allow", "HEAD").build();
                Response mapped = new HttpExceptionMapper().toResponse(new WebApplicationException(original))) {
            assertEquals(original.getAllowedMethods(), mapped.getAllowedMethods());
        }
        try (Response original = Response.status(401)
                .header("WWW-Authenticate", "Bearer error_description=SECRET_MARKER").build();
                Response mapped = new HttpExceptionMapper().toResponse(new WebApplicationException(original))) {
            assertEquals("Bearer", mapped.getHeaderString("WWW-Authenticate"));
        }
        for (String retry : new String[]{"120", "Wed, 21 Oct 2015 07:28:00 GMT"}) {
            try (Response original = Response.status(503).header("Retry-After", retry).build();
                    Response mapped = new HttpExceptionMapper().toResponse(new WebApplicationException(original))) {
                assertEquals(retry, mapped.getHeaderString("Retry-After"));
            }
        }
    }
}
