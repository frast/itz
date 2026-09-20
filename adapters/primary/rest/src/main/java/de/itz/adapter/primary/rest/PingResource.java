package de.itz.adapter.primary.rest;

import org.jboss.logging.Logger;

import de.itz.adapter.primary.rest.generated.api.PingApi;
import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;
import de.itz.domain.Ping;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@RequestScoped
public class PingResource implements PingApi {

    private static final Logger LOG = Logger.getLogger(PingResource.class);

    private final PingUseCase useCase;

    // RESTEasy requires public visibility; CDI client proxies must be able to call this constructor.
    public PingResource() {
        this(() -> {
            throw new IllegalStateException("PingResource must be constructed by CDI");
        });
    }

    @Inject
    public PingResource(PingUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Response ping() {
        LOG.info("Handling ping request");
        Ping ping = useCase.execute();
        LOG.info("Ping request completed");
        return Response.ok(new PingResponse(ping.message())).build();
    }
}
