package de.itz.application.security;

import java.util.Set;

import de.itz.domain.security.Role;

public record CurrentUser(String name, Set<Role> roles) {
    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasPermission(Permission permission) {
        return switch (permission) {
            case PING -> hasRole(Role.SPECIAL);
        };
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
