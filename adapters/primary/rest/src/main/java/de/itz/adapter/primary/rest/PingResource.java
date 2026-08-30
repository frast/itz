package de.itz.adapter.primary.rest;

import de.itz.adapter.primary.rest.generated.api.PingApi;
import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;
import de.itz.domain.Ping;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class PingResource implements PingApi {
    private final PingUseCase useCase;

    /**
     * Do not use or remove this constructor, it is only for the framework to create an instance of this class. Use the
     * constructor with the PingUseCase parameter instead.
     */
    public PingResource() {
        useCase = null;
    }

    @Inject
    public PingResource(PingUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public PingResponse ping() {
        Ping ping = useCase.execute();
        return new PingResponse(ping.message());
    }
}
