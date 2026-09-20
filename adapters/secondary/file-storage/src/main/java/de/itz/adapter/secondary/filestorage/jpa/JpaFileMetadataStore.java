package de.itz.adapter.secondary.filestorage.jpa;

import java.util.Objects;

import de.itz.domain.file.UploadedFile;
import jakarta.enterprise.context.Dependent;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.UserTransaction;
import jakarta.transaction.Transactional;

@Dependent
public class JpaFileMetadataStore {
    private final EntityManager entityManager;
    private final UserTransaction transaction;

    @Inject
    public JpaFileMetadataStore(EntityManager entityManager, UserTransaction transaction) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.transaction = Objects.requireNonNull(transaction);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void save(UploadedFile file, String storageKey) {
        // NOT_SUPPORTED suspends the caller's transaction and permits UserTransaction.
        // Commit stays explicit so its outcome is known before reporting success.
        try {
            transaction.begin();
        } catch (Exception exception) {
            throw new FileMetadataStorageException(exception, true);
        }
        try {
            entityManager.persist(new UploadedFileEntity(file, storageKey));
            entityManager.flush();
        } catch (RuntimeException exception) {
            boolean rolledBack = false;
            try {
                transaction.rollback();
                rolledBack = true;
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw new FileMetadataStorageException(exception, rolledBack);
        }
        try {
            transaction.commit();
        } catch (RollbackException | HeuristicRollbackException exception) {
            throw new FileMetadataStorageException(exception, true);
        } catch (Exception exception) {
            // A failed commit response does not prove that the database rolled back.
            throw new FileMetadataStorageException(exception, false);
        }
    }
}
