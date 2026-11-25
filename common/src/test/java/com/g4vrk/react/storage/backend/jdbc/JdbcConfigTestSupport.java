/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.storage.backend.jdbc;

import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link JdbcExecutor} transaction rollback guarantees on real SQL connections.
 */
public final class JdbcConfigTestSupport {

    private JdbcConfigTestSupport() {

        throw new UnsupportedOperationException("Utility class");

    }

    public static void assertTransactionRollsBack(
            final @NotNull Connection singleConnection
    ) throws SQLException {

        try (Statement statement = singleConnection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS rollback_guard (id INT PRIMARY KEY)");
            statement.execute("DELETE FROM rollback_guard");
        }
        final DataSource single = new NonClosingDataSource(singleConnection);
        final JdbcExecutor executor = new JdbcExecutor(single);
        assertThrows(IllegalStateException.class, () -> executor.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rollback_guard (id) VALUES (?)")) {
                insert.setInt(1, 42);
                insert.executeUpdate();
            }
            throw new IllegalStateException("Simulated mid-transaction failure");
        }));

        try (Statement statement = singleConnection.createStatement();
             ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM rollback_guard")) {
            count.next();
            assertEquals(0, count.getInt(1));
        }

    }

    private record NonClosingDataSource(@NotNull Connection connection) implements DataSource {

        @Override
        public Connection getConnection() {

            return nonClosingConnection(connection);

        }

        @Override
        public Connection getConnection(final String username, final String password) {

            return nonClosingConnection(connection);

        }

        private static @NotNull Connection nonClosingConnection(
                final @NotNull Connection delegate
        ) {

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (final InvocationTargetException invocationFailure) {
                            throw invocationFailure.getCause();
                        }
                    }
            );

        }

        @Override
        public PrintWriter getLogWriter() {

            return null;

        }

        @Override
        public void setLogWriter(final PrintWriter out) {

        }

        @Override
        public void setLoginTimeout(final int seconds) {

        }

        @Override
        public int getLoginTimeout() {

            return 0;

        }

        @Override
        public Logger getParentLogger() {

            return Logger.getGlobal();

        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {

            throw new SQLException("Unsupported");

        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {

            return false;

        }

    }

}
