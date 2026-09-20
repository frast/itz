package de.itz.adapter.primary.rest;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.itz.application.security.CurrentUser;
import de.itz.domain.security.Role;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

@Provider
@Dependent
@Priority(Priorities.AUTHENTICATION)
public class CurrentUserFilter implements ContainerRequestFilter {
    private final RequestCurrentUserContext currentUserContext;

    // RESTEasy 6.2 requires a public JAX-RS constructor before delegating creation to CDI.
    public CurrentUserFilter() {
        throw new IllegalStateException("CurrentUserFilter must be constructed by CDI");
    }

    @Inject
    public CurrentUserFilter(RequestCurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    @Override
    @SuppressWarnings("null") // JDT cannot model JAX-RS overrides and nullness of legacy external APIs.
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext context = requestContext.getSecurityContext();
        Principal principal = Objects.requireNonNull(context.getUserPrincipal(),
                "Authenticated request has no user principal");
        String principalName = Objects.requireNonNull(principal.getName(), "Authenticated user principal has no name");
        Set<Role> roles = Stream.of(Role.values())
                .filter(role -> context.isUserInRole(role.externalName()))
                .collect(Collectors.toUnmodifiableSet());
        currentUserContext.initialize(new CurrentUser(principalName, roles));
    }
}
