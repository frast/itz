package de.itz.adapter.primary.rest;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.itz.application.security.CurrentUser;
import de.itz.application.security.Permission;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CurrentUserFilter implements ContainerRequestFilter {
    @Inject
    private RequestCurrentUserContext currentUserContext;

    @Context
    private SecurityContext securityContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Set<String> roles = Stream.of("user", "admin", "special")
                .filter(securityContext::isUserInRole)
                .collect(Collectors.toUnmodifiableSet());
        Set<Permission> permissions = securityContext.isUserInRole("special")
                ? Set.of(Permission.PING)
                : Set.of();
        currentUserContext.setCurrentUser(new CurrentUser(
                securityContext.getUserPrincipal().getName(), roles, permissions));
    }
}
