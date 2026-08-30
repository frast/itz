package de.itz.application;

import de.itz.application.security.CurrentUser;
import de.itz.application.security.CurrentUserContext;
import de.itz.application.security.ForbiddenException;
import de.itz.application.security.Permission;
import de.itz.domain.Ping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PingApplicationService implements PingUseCase {
    private final CurrentUserContext currentUserContext;

    @Inject
    public PingApplicationService(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    @Override
    public Ping execute() {
        CurrentUser user = currentUserContext.currentUser();
        if (!user.hasPermission(Permission.PING)) {
            throw new ForbiddenException("The current user is not allowed to ping");
        }
        return Ping.alive();
    }
}
