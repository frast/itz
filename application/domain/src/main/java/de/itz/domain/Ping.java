package de.itz.domain;

public record Ping(String message) {
    public static Ping alive() {
        return new Ping("pong");
    }
}
