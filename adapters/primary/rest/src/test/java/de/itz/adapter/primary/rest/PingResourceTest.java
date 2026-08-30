package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.weld.junit5.auto.ActivateScopes;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingApplicationService;
import de.itz.application.security.CurrentUser;
import de.itz.application.security.CurrentUserContext;
import de.itz.application.security.Permission;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@EnableAutoWeld
@AddBeanClasses({PingResource.class, PingApplicationService.class, RequestCurrentUserContext.class})
@ActivateScopes(RequestScoped.class)
class PingResourceTest {
    @Inject
    private PingResource resource;

    @Inject
    private CurrentUserContext currentUserContext;

    @org.junit.jupiter.api.BeforeEach
    void authenticatedSpecialUser() {
        ((RequestCurrentUserContext) currentUserContext).initialize(new CurrentUser(
                "special-user", java.util.Set.of("user", "special"), java.util.Set.of(Permission.PING)));
    }

    @Test
    void mapsDomainResultToGeneratedResponse() {
        PingResponse response = resource.ping();

        assertEquals("pong", response.getMessage());
    }
}
