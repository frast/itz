package de.itz.adapter.primary.rest;

import java.util.Objects;
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
import org.jspecify.annotations.Nullable;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CurrentUserFilter implements ContainerRequestFilter {
    @Inject
    private RequestCurrentUserContext currentUserContext;

    @Context
    private @Nullable SecurityContext securityContext;

    @Override
    @SuppressWarnings("null") // JDT cannot model JAX-RS overrides and nullness of legacy external APIs.
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext context = Objects.requireNonNull(securityContext, "JAX-RS SecurityContext was not injected");
        Set<String> roles = Stream.of("user", "admin", "special")
                .filter(context::isUserInRole)
                .collect(Collectors.toUnmodifiableSet());
        Set<Permission> permissions = context.isUserInRole("special")
                ? Set.of(Permission.PING)
                : Set.of();
        currentUserContext.setCurrentUser(new CurrentUser(
                context.getUserPrincipal().getName(), roles, permissions));
    }
}
