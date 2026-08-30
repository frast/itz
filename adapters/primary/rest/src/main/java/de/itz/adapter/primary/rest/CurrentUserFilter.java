package de.itz.adapter.primary.rest;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.itz.application.security.CurrentUser;
import de.itz.domain.security.Role;
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
        Principal principal = Objects.requireNonNull(context.getUserPrincipal(),
                "Authenticated request has no user principal");
        String principalName = Objects.requireNonNull(principal.getName(), "Authenticated user principal has no name");
        Set<Role> roles = Stream.of(Role.values())
                .filter(role -> context.isUserInRole(role.externalName()))
                .collect(Collectors.toUnmodifiableSet());
        currentUserContext.initialize(new CurrentUser(principalName, roles));
    }
}
