package de.itz.adapter.primary.rest;

import de.itz.adapter.primary.rest.generated.api.PingApi;
import de.itz.adapter.primary.rest.generated.model.PingResponse;
import de.itz.application.PingUseCase;
import de.itz.domain.Ping;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class PingResource implements PingApi {

    @Inject
    private PingUseCase useCase;

    @Override
    public PingResponse ping() {
        Ping ping = useCase.execute();
        return new PingResponse(ping.message());
    }
}
