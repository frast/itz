package de.itz.adapter.primary.rest;

import de.itz.adapter.primary.rest.generated.api.PingApi;
import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;
import de.itz.domain.Ping;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@RequestScoped
public class PingResource implements PingApi {

    private static final Logger LOG = Logger.getLogger(PingResource.class);

    @Inject
    private PingUseCase useCase;

    @Override
    public PingResponse ping() {
        LOG.info("Handling ping request");
        Ping ping = useCase.execute();
        LOG.info("Ping request completed");
        return new PingResponse(ping.message());
    }
}
