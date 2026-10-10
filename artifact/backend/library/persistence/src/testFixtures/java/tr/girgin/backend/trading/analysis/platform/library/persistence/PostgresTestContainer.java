package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * For the catalog checks that run without a Spring context and need many empty databases: one PostgreSQL container
 * per test JVM (started on first use, stopped with the JVM by Testcontainers) and a fresh database in it for every
 * call of {@link #newDataSource()}. Spring tests do not use it: they get a container per context from
 * {@link TestDatabaseConfiguration}. The image tag lives here, in the one literal that Renovate's manager reads, and
 * {@link TestDatabaseConfiguration} uses it too. The container is not reused across JVM runs (no reuse flag). Needs
 * Docker or Podman; the image is the Compose file's PostgreSQL.
 */
final class PostgresTestContainer {

    static final String IMAGE = "postgres:18.6";

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private PostgresTestContainer() {}

    /**
     * Creates a fresh database in the container and returns a data source for it: one connection per use, no pool to
     * close.
     *
     * @throws IllegalStateException if the database cannot be created
     */
    static DataSource newDataSource() {
        PostgreSQLContainer postgres = Container.INSTANCE;
        String name = "test_" + COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the test database " + name, e);
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name);
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    /** Starts the container the first time it is used. */
    private static final class Container {

        static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer(IMAGE);

        static {
            INSTANCE.start();
        }
    }
}
