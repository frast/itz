package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.domain.Ping;
import jakarta.ws.rs.core.Response;

class PingResourceTest {
    @Test
    void mapsDomainResultToGeneratedResponse() {
        PingResource resource = new PingResource(Ping::alive);

        try (Response response = resource.ping()) {
            assertEquals(200, response.getStatus());
            assertEquals("pong", ((PingResponse) response.getEntity()).getMessage());
        }
    }
}
