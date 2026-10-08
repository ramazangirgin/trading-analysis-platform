package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container per test JVM (started on first use, stopped with the JVM by Testcontainers)
 * and a fresh database in it for every call of {@link #create()}, so test classes never see each
 * other's rows. Needs Docker or Podman; the image is the Compose file's PostgreSQL.
 */
public final class PostgresTestDatabase {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private PostgresTestDatabase() {}

    public static Database create() {
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
        return new Database(url, postgres.getUsername(), postgres.getPassword());
    }

    /** The JDBC URL and credentials of one database. */
    public record Database(String url, String username, String password) {

        /** A data source for the database: one connection per use, which is enough for a test. */
        public DataSource dataSource() {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setUrl(url);
            dataSource.setUser(username);
            dataSource.setPassword(password);
            return dataSource;
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
