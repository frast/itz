package de.itz.adapter.primary.rest;

import de.itz.application.security.CurrentUser;
import de.itz.application.security.CurrentUserContext;
import de.itz.application.security.UnauthorizedException;
import jakarta.enterprise.context.RequestScoped;
import org.jspecify.annotations.Nullable;

@RequestScoped
public class RequestCurrentUserContext implements CurrentUserContext {
    private @Nullable CurrentUser currentUser;

    public void setCurrentUser(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public CurrentUser currentUser() {
        if (currentUser == null) {
            throw new UnauthorizedException("No authenticated user in the current request");
        }
        return currentUser;
    }
}
