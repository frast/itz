package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.PingApplicationService;
import de.itz.application.security.CurrentUser;
import de.itz.application.security.ForbiddenException;
import de.itz.application.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class SecurityExceptionTest {
    @Test
    void mapsUnauthorizedExceptionToStableResponse() {
        CapturingUnauthorizedExceptionMapper mapper = new CapturingUnauthorizedExceptionMapper();

        mapper.toResponse(new UnauthorizedException("Sensitive internal detail"));

        assertEquals(Response.Status.UNAUTHORIZED, mapper.status);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mapper.mediaType);
        assertError(mapper.error, "UNAUTHORIZED", "Authentication is required");
    }

    @Test
    void mapsForbiddenExceptionToStableResponse() {
        CapturingForbiddenExceptionMapper mapper = new CapturingForbiddenExceptionMapper();

        mapper.toResponse(new ForbiddenException("Sensitive internal detail"));

        assertEquals(Response.Status.FORBIDDEN, mapper.status);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mapper.mediaType);
        assertError(mapper.error, "FORBIDDEN", "The current user is not allowed to perform this operation");
    }

    @Test
    void rejectsUserWithoutPingPermissionAsForbidden() {
        CurrentUser currentUser = new CurrentUser("regular-user", Set.of("user"), Set.of());
        PingApplicationService service = new PingApplicationService(() -> currentUser);

        assertThrows(ForbiddenException.class, service::execute);
    }

    @Test
    void rejectsMissingRequestUserAsUnauthorized() {
        RequestCurrentUserContext currentUserContext = new RequestCurrentUserContext();

        assertThrows(UnauthorizedException.class, currentUserContext::currentUser);
    }

    private void assertError(ErrorResponse error, String expectedCode, String expectedMessage) {
        assertEquals(expectedCode, error.getCode());
        assertEquals(expectedMessage, error.getMessage());
    }

    private static final class CapturingUnauthorizedExceptionMapper extends UnauthorizedExceptionMapper {
        private Response.Status status;
        private MediaType mediaType;
        private ErrorResponse error;

        @Override
        Response buildResponse(Response.Status status, MediaType mediaType, ErrorResponse error) {
            this.status = status;
            this.mediaType = mediaType;
            this.error = error;
            return null;
        }
    }

    private static final class CapturingForbiddenExceptionMapper extends ForbiddenExceptionMapper {
        private Response.Status status;
        private MediaType mediaType;
        private ErrorResponse error;

        @Override
        Response buildResponse(Response.Status status, MediaType mediaType, ErrorResponse error) {
            this.status = status;
            this.mediaType = mediaType;
            this.error = error;
            return null;
        }
    }
}
