package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import de.itz.application.PingApplicationService;
import de.itz.application.security.CurrentUser;
import de.itz.application.security.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class SecurityExceptionTest {
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
    void reportsUninitializedRequestUserContext() {
        RequestCurrentUserContext currentUserContext = new RequestCurrentUserContext();

        assertThrows(IllegalStateException.class, currentUserContext::currentUser);
    }

    private void assertError(ErrorResponse error, String expectedCode, String expectedMessage) {
        assertEquals(expectedCode, error.getCode());
        assertEquals(expectedMessage, error.getMessage());
    }
}
