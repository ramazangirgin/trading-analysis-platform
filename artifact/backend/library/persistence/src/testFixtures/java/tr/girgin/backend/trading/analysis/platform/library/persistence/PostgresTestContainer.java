package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container per test JVM (started on first use, stopped with the JVM by Testcontainers) and a fresh
 * database in it for every call of {@link #newDatabase()}, so test classes never see each other's rows. Needs
 * Docker or Podman; the image is the Compose file's PostgreSQL.
 *
 * <p>This is not a {@code @ServiceConnection}: a container bean would start one container per Spring context, and
 * a static container would give every context the same database, where the migration tests' seeded Flyway
 * histories clash. The container is not reused across JVM runs either (no reuse flag). The details of a new
 * database are a {@link JdbcConnectionDetails}, the contract that a service connection produces too, so
 * {@code DataSourceAutoConfiguration} builds the pool from it (see {@link TestDatabaseConfiguration}).
 */
final class PostgresTestContainer {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private PostgresTestContainer() {}

    /**
     * Creates a fresh database in the container and returns the details to connect to it.
     *
     * @throws IllegalStateException if the database cannot be created
     */
    static JdbcConnectionDetails newDatabase() {
        PostgreSQLContainer postgres = Container.INSTANCE;
        String name = "test_" + COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the test database " + name, e);
        }
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
        return new Details(url, postgres.getUsername(), postgres.getPassword());
    }

    /** A data source for the database: one connection per use, for the checks that run without a Spring context. */
    static DataSource dataSource(JdbcConnectionDetails details) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(details.getJdbcUrl());
        dataSource.setUser(details.getUsername());
        dataSource.setPassword(details.getPassword());
        return dataSource;
    }

    private record Details(String url, String username, String password) implements JdbcConnectionDetails {

        @Override
        public String getJdbcUrl() {
            return url;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }
    }

    /** Starts the container the first time it is used. */
    private static final class Container {

        static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer("postgres:18.6");

        static {
            INSTANCE.start();
        }
    }
}
