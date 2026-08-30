package de.itz.application.security;

import java.util.Set;

import de.itz.domain.security.Role;

public record CurrentUser(String name, Set<Role> roles, Set<Permission> permissions) {
    public CurrentUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
