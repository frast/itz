package de.itz.adapter.secondary.filestorage.jpa;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Dependent
public class PersistenceResources {
    private @Nullable EntityManager entityManager;

    @PersistenceContext(unitName = "itzPU")
    protected void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Produces
    @Dependent
    EntityManager entityManager() {
        // Forward the container's transaction-aware reference; its lifecycle stays with EAP.
        return Objects.requireNonNull(entityManager, "Persistence context was not injected");
    }
}
