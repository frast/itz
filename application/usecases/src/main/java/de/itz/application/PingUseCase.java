package de.itz.application;

import de.itz.domain.Ping;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PingUseCase {
    public Ping execute() {
        return Ping.alive();
    }
}
