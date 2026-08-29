package de.itz.application;

import de.itz.domain.Ping;

public class PingUseCase {
    public Ping execute() {
        return Ping.alive();
    }
}
