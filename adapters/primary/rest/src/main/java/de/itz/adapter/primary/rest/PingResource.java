package de.itz.adapter.primary.rest;

import de.itz.application.PingUseCase;
import de.itz.domain.Ping;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("ping")
@Produces(MediaType.APPLICATION_JSON)
public class PingResource {
    private final PingUseCase useCase;

    public PingResource() {
        this.useCase = null;
    }

    @Inject
    public PingResource(PingUseCase useCase) {
        this.useCase = useCase;
    }
    @GET public Ping ping() { return useCase.execute(); }
}
