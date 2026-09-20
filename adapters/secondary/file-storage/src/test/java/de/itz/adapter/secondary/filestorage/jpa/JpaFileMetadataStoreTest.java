package de.itz.adapter.secondary.filestorage.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.UUID;

import javax.sql.XAConnection;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.transaction.jta.platform.internal.JBossStandAloneJtaPlatform;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import de.itz.domain.file.UploadedFile;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

class JpaFileMetadataStoreTest {
    @Test
    void commitsMetadataBeforeReturningAndRollsBackConstraintFailure() throws Exception {
        com.arjuna.ats.arjuna.common.arjPropertyManager.getObjectStoreEnvironmentBean()
                .setObjectStoreDir("target/transaction-object-store");
        try (XaConnections connections = new XaConnections()) {
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .addService(ConnectionProvider.class, connections)
                    .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                    .applySetting("hibernate.transaction.coordinator_class", "jta")
                    .applySetting("hibernate.transaction.jta.platform", new JBossStandAloneJtaPlatform())
                    .build();
            try (SessionFactory factory = new MetadataSources(registry)
                    .addAnnotatedClass(UploadedFileEntity.class).buildMetadata().buildSessionFactory()) {
                UserTransaction tx = com.arjuna.ats.jta.UserTransaction.userTransaction();
                UploadedFile file = new UploadedFile(UUID.randomUUID(), "é".repeat(255),
                        "text/plain; note=\"" + "é".repeat(493) + "\"", Long.MAX_VALUE);
                String key = file.id() + ".bin";
                try (EntityManager writer = factory.createEntityManager()) {
                    JpaFileMetadataStore store = store(writer, tx);
                    store.save(file, key);
                }
                assertEquals(Status.STATUS_NO_TRANSACTION, tx.getStatus());

                // A fresh persistence context and connection must see committed data.
                try (EntityManager reader = factory.createEntityManager()) {
                    @Nullable
                    UploadedFileEntity entity = reader.find(UploadedFileEntity.class, file.id().toString());
                    assertNotNull(entity);
                    assertEquals(file, entity.toDomain());
                    assertEquals(key, entity.storageKey());
                }

                UploadedFile duplicate = new UploadedFile(UUID.randomUUID(), "duplicate.txt", "text/plain", 3);
                try (EntityManager writer = factory.createEntityManager()) {
                    FileMetadataStorageException failure = assertThrows(FileMetadataStorageException.class,
                            () -> store(writer, tx).save(duplicate, key));
                    assertTrue(failure.rollbackConfirmed());
                }
                assertEquals(Status.STATUS_NO_TRANSACTION, tx.getStatus());
                try (EntityManager reader = factory.createEntityManager()) {
                    assertEquals(1L, reader.createQuery("select count(f) from UploadedFileEntity f", Long.class)
                            .getSingleResult());
                }

                UploadedFile rolledBack = new UploadedFile(UUID.randomUUID(), "rollback.txt", "text/plain", 1);
                try (EntityManager writer = factory.createEntityManager()) {
                    FileMetadataStorageException failure = assertThrows(FileMetadataStorageException.class,
                            () -> store(writer, failingCommit(tx, true)).save(rolledBack, rolledBack.id() + ".bin"));
                    assertTrue(failure.rollbackConfirmed());
                }
                assertEquals(Status.STATUS_NO_TRANSACTION, tx.getStatus());

                UploadedFile uncertain = new UploadedFile(UUID.randomUUID(), "uncertain.txt", "text/plain", 1);
                try (EntityManager writer = factory.createEntityManager()) {
                    FileMetadataStorageException failure = assertThrows(FileMetadataStorageException.class,
                            () -> store(writer, failingCommit(tx, false)).save(uncertain, uncertain.id() + ".bin"));
                    assertFalse(failure.rollbackConfirmed());
                }
                assertEquals(Status.STATUS_NO_TRANSACTION, tx.getStatus());
                try (EntityManager reader = factory.createEntityManager()) {
                    assertEquals(2L, reader.createQuery("select count(f) from UploadedFileEntity f", Long.class)
                            .getSingleResult());
                    // A lost commit response really can leave a committed metadata row.
                    assertNotNull(reader.find(UploadedFileEntity.class, uncertain.id().toString()));
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
    }

    private UserTransaction failingCommit(UserTransaction transaction, boolean rollback) {
        return (UserTransaction) Proxy.newProxyInstance(UserTransaction.class.getClassLoader(),
                new Class<?>[]{UserTransaction.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("commit") && rollback) {
                        transaction.setRollbackOnly();
                    }
                    try {
                        Object result = method.invoke(transaction, arguments);
                        if (method.getName().equals("commit")) {
                            throw new SystemException("Simulated lost commit response");
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw Objects.requireNonNull(exception.getCause());
                    }
                });
    }

    private JpaFileMetadataStore store(EntityManager manager, UserTransaction transaction) {
        return new JpaFileMetadataStore(manager, transaction);
    }

    /** Enlists real H2 XA connections in Narayana; no transaction or database mocks. */
    private static final class XaConnections implements ConnectionProvider, AutoCloseable {
        private final JdbcDataSource dataSource = new JdbcDataSource();
        private final IdentityHashMap<Connection, XAConnection> connections = new IdentityHashMap<>();

        private XaConnections() {
            dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=Oracle");
        }

        @Override
        public Connection getConnection() throws SQLException {
            XAConnection xa = dataSource.getXAConnection();
            Connection connection = xa.getConnection();
            try {
                TransactionManager manager = com.arjuna.ats.jta.TransactionManager.transactionManager();
                Transaction transaction = manager.getTransaction();
                if (transaction != null) {
                    if (!transaction.enlistResource(xa.getXAResource())) {
                        throw new SQLException("XA resource enlistment failed");
                    }
                }
                connections.put(connection, xa);
                return connection;
            } catch (Exception exception) {
                xa.close();
                throw new SQLException("Cannot enlist test connection", exception);
            }
        }

        @Override
        public void closeConnection(@Nullable Connection connection) throws SQLException {
            Objects.requireNonNull(connection).close();
            Objects.requireNonNull(connections.remove(connection)).close();
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(@Nullable Class<?> type) {
            return Objects.requireNonNull(type).isInstance(this);
        }

        @Override
        public <T> T unwrap(@Nullable Class<T> type) {
            return Objects.requireNonNull(type).cast(this);
        }

        @Override
        public void close() throws SQLException {
            for (XAConnection connection : connections.values()) {
                connection.close();
            }
            connections.clear();
        }
    }
}
