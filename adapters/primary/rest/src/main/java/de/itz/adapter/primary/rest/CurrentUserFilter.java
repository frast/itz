package de.itz.adapter.primary.rest;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.itz.application.security.CurrentUser;
import de.itz.application.security.Role;
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
    private static final Map<String, Role> ROLE_MAPPING = Map.of("user", Role.USER, "special", Role.SPECIAL, "admin",
            Role.ADMIN);
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
        Set<Role> roles = ROLE_MAPPING.entrySet().stream()
                .filter(role -> context.isUserInRole(role.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toUnmodifiableSet());
        currentUserContext.initialize(new CurrentUser(principalName, roles));
    }
}
