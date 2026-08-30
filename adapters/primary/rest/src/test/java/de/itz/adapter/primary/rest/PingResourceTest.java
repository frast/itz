package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.auto.ActivateScopes;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;

@EnableAutoWeld
@AddBeanClasses({PingResource.class, PingUseCase.class})
@ActivateScopes(RequestScoped.class)
class PingResourceTest {
    @Inject
    private PingResource resource;

    @Test
    void mapsDomainResultToGeneratedResponse() {
        PingResponse response = resource.ping();

        assertEquals("pong", response.getMessage());
    }
}
