package de.itz.application.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.itz.domain.security.Role;

class CurrentUserTest {
    @Test
    @SuppressWarnings({"NullAway", "null"}) // Exercise runtime validation for callers without nullness analysis.
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> new CurrentUser(null, Set.of()));
    }

    @Test
    @SuppressWarnings({"NullAway", "null"}) // Exercise runtime validation for callers without nullness analysis.
    void rejectsNullRoles() {
        assertThrows(NullPointerException.class, () -> new CurrentUser("user", null));
    }

    @Test
    void retainsRolesWhenOriginalSetChanges() {
        Set<Role> roles = new HashSet<>(Set.of(Role.USER));
        CurrentUser currentUser = new CurrentUser("user", roles);

        roles.clear();
        roles.add(Role.ADMIN);

        assertEquals(Set.of(Role.USER), currentUser.roles());
    }

    @Test
    void exposesUnmodifiableRoles() {
        CurrentUser currentUser = new CurrentUser("user", Set.of(Role.USER));

        assertThrows(UnsupportedOperationException.class, () -> currentUser.roles().add(Role.ADMIN));
        assertEquals(Set.of(Role.USER), currentUser.roles());
    }

    @Test
    void reportsWhetherUserHasRole() {
        CurrentUser currentUser = new CurrentUser("special-user", Set.of(Role.USER, Role.SPECIAL));

        assertTrue(currentUser.hasRole(Role.SPECIAL));
        assertFalse(currentUser.hasRole(Role.ADMIN));
    }

    @Test
    void derivesPingPermissionFromSpecialRole() {
        CurrentUser specialUser = new CurrentUser("special-user", Set.of(Role.SPECIAL));
        CurrentUser regularUser = new CurrentUser("regular-user", Set.of(Role.USER));

        assertTrue(specialUser.hasPermission(Permission.PING));
        assertFalse(regularUser.hasPermission(Permission.PING));
    }
}
