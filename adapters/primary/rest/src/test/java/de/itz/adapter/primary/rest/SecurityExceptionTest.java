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
        UnauthorizedExceptionMapper mapper = new UnauthorizedExceptionMapper();

        Response response = mapper.toResponse(new UnauthorizedException("Sensitive internal detail"));

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertError((ErrorResponse) response.getEntity(), "UNAUTHORIZED", "Authentication is required");
    }

    @Test
    void mapsForbiddenExceptionToStableResponse() {
        ForbiddenExceptionMapper mapper = new ForbiddenExceptionMapper();

        Response response = mapper.toResponse(new ForbiddenException("Sensitive internal detail"));

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertError((ErrorResponse) response.getEntity(), "FORBIDDEN",
                "The current user is not allowed to perform this operation");
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
}
