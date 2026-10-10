package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersLifecycleApplicationContextInitializer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves {@link TestDatabaseConfiguration}: the auto-configured Hikari pool, a container of its own for every
 * context, and a container that stops with its context.
 */
class TestDatabaseConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new TestcontainersLifecycleApplicationContextInitializer())
            .withConfiguration(
                    AutoConfigurations.of(DataSourceAutoConfiguration.class, ServiceConnectionAutoConfiguration.class))
            .withUserConfiguration(TestDatabaseConfiguration.class);

    @Test
    void autoConfigurationBuildsAHikariPoolFromTheServiceConnection() {
        runner.run(context -> assertEquals(
                "HikariDataSource", context.getBean(DataSource.class).getClass().getSimpleName()));
    }

    @Test
    void everyContextHasAContainerOfItsOwn() {
        runner.run(first -> runner.run(second -> {
            URI one = jdbcUri(first.getBean(DataSource.class));
            URI other = jdbcUri(second.getBean(DataSource.class));

            assertNotEquals(one.getPort(), other.getPort());
        }));
    }

    @Test
    void aTableOfOneContextDoesNotExistInTheOther() {
        runner.run(first -> runner.run(second -> {
            execute(first.getBean(DataSource.class), "CREATE TABLE \"ONLY_IN_ONE\" (\"ID\" integer)");

            assertTrue(tableExists(first.getBean(DataSource.class)));
            assertFalse(tableExists(second.getBean(DataSource.class)));
        }));
    }

    @Test
    void closingTheContextStopsItsContainer() {
        AtomicReference<PostgreSQLContainer> container = new AtomicReference<>();
        runner.run(context -> {
            container.set(context.getBean(PostgreSQLContainer.class));
            assertTrue(container.get().isRunning());
        });

        assertFalse(container.get().isRunning());
    }

    private static URI jdbcUri(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
        }
    }

    private static void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean tableExists(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var tables = connection.getMetaData().getTables(null, null, "ONLY_IN_ONE", null)) {
            return tables.next();
        }
    }
}
