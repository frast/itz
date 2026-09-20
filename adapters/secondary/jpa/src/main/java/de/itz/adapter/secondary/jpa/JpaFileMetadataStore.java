package de.itz.adapter.secondary.jpa;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import de.itz.domain.file.UploadedFile;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.UserTransaction;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class JpaFileMetadataStore {
    private @Nullable EntityManager entityManager;
    private @Nullable UserTransaction transaction;

    @PersistenceContext(unitName = "itzPU")
    protected void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Resource
    protected void setTransaction(UserTransaction transaction) {
        this.transaction = transaction;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void save(UploadedFile file, String storageKey) {
        EntityManager manager = Objects.requireNonNull(entityManager);
        UserTransaction tx = Objects.requireNonNull(transaction);
        // NOT_SUPPORTED suspends the caller's transaction and permits UserTransaction.
        // Commit stays explicit so its outcome is known before reporting success.
        try {
            tx.begin();
        } catch (Exception exception) {
            throw new FileMetadataStorageException(exception, true);
        }
        try {
            manager.persist(new UploadedFileEntity(file, storageKey));
            manager.flush();
        } catch (RuntimeException exception) {
            boolean rolledBack = false;
            try {
                tx.rollback();
                rolledBack = true;
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw new FileMetadataStorageException(exception, rolledBack);
        }
        try {
            tx.commit();
        } catch (RollbackException | HeuristicRollbackException exception) {
            throw new FileMetadataStorageException(exception, true);
        } catch (Exception exception) {
            // A failed commit response does not prove that the database rolled back.
            throw new FileMetadataStorageException(exception, false);
        }
    }
}
