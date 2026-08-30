package de.itz.adapter.primary.rest;

import java.util.Objects;

import de.itz.application.security.CurrentUser;
import de.itz.application.security.CurrentUserContext;
import jakarta.enterprise.context.RequestScoped;
import org.jspecify.annotations.Nullable;

@RequestScoped
public class RequestCurrentUserContext implements CurrentUserContext {
    private @Nullable CurrentUser currentUser;

    void initialize(CurrentUser currentUser) {
        if (this.currentUser != null) {
            throw new IllegalStateException("Current user context was already initialized for this request");
        }
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser");
    }

    @Override
    public CurrentUser currentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("Current user context was not initialized for this request");
        }
        return currentUser;
    }
}
