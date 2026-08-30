package de.itz.application.security;

import java.util.Set;

public record CurrentUser(String name, Set<String> roles, Set<Permission> permissions) {
    public CurrentUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
