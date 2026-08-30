package de.itz.application.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.itz.domain.security.Role;

class CurrentUserTest {
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
