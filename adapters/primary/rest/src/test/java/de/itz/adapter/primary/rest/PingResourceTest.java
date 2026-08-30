package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;
import org.junit.jupiter.api.Test;

class PingResourceTest {
    @Test
    void mapsDomainResultToGeneratedResponse() {
        PingResource resource = new PingResource(new PingUseCase());

        PingResponse response = resource.ping();

        assertEquals("pong", response.getMessage());
    }
}
