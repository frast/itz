package de.itz.adapter.secondary.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.UUID;

import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.arjuna.ats.jta.cdi.TransactionExtension;
import com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorNotSupported;

import de.itz.domain.file.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

@EnableAutoWeld
@AddExtensions(TransactionExtension.class)
@AddBeanClasses({JpaFileMetadataStoreCdiTest.TestStore.class,
        TransactionalInterceptorNotSupported.class})
class JpaFileMetadataStoreCdiTest {
    @Test
    void suspendsAndResumesCallerOnSuccessAndFailure() throws Exception {
        TransactionManager manager = com.arjuna.ats.jta.TransactionManager.transactionManager();
        @Nullable
        TestStore store = CDI.current().select(TestStore.class).get();
        for (boolean fail : new boolean[]{false, true}) {
            manager.begin();
            Transaction caller = manager.getTransaction();
            try {
                UploadedFile file = new UploadedFile(UUID.randomUUID(), "test.txt", "text/plain", 1);
                if (fail) {
                    assertThrows(FileMetadataStorageException.class, () -> store.save(file, "fail"));
                } else {
                    store.save(file, "success");
                }
                assertSame(caller, manager.getTransaction());
                assertEquals(Status.STATUS_ACTIVE, manager.getStatus());
                assertNotSame(caller, store.persistedTransaction());
                assertEquals(fail ? Status.STATUS_ROLLEDBACK : Status.STATUS_COMMITTED,
                        store.persistedTransaction().getStatus());
            } finally {
                manager.rollback();
            }
        }
    }

    @ApplicationScoped
    public static class TestStore extends JpaFileMetadataStore {
        private java.util.Optional<Transaction> persisted = java.util.Optional.empty();

        public TestStore() {
            setTransaction(com.arjuna.ats.jta.UserTransaction.userTransaction());
            setEntityManager((EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                    new Class<?>[]{EntityManager.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("persist")) {
                            TransactionManager manager = com.arjuna.ats.jta.TransactionManager.transactionManager();
                            assertEquals(Status.STATUS_ACTIVE, manager.getStatus());
                            persisted = java.util.Optional.ofNullable(manager.getTransaction());
                            UploadedFileEntity entity = (UploadedFileEntity) java.util.Objects
                                    .requireNonNull(arguments)[0];
                            if (entity.storageKey().equals("fail")) {
                                throw new IllegalStateException("Simulated persistence failure");
                            }
                        }
                        return null;
                    }));
        }

        public Transaction persistedTransaction() {
            return persisted.orElseThrow();
        }
    }
}
