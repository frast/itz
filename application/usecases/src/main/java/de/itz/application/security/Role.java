package de.itz.application.security;

public enum Role {
    USER("user"), ADMIN("admin"), SPECIAL("special");

    private final String externalName;

    Role(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }
}
