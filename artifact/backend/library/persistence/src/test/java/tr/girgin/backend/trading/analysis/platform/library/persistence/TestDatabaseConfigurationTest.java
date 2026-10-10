package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves {@link TestDatabaseConfiguration}: the auto-configured Hikari pool, one container for the JVM, and a
 * database of its own for every context.
 */
class TestDatabaseConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(TestDatabaseConfiguration.class);

    @Test
    void autoConfigurationBuildsAHikariPoolFromTheDetails() {
        runner.run(context -> assertEquals(
                "HikariDataSource", context.getBean(DataSource.class).getClass().getSimpleName()));
    }

    @Test
    void contextsShareTheContainerButNotTheDatabase() {
        runner.run(first -> runner.run(second -> {
            URI one = jdbcUri(first.getBean(DataSource.class));
            URI other = jdbcUri(second.getBean(DataSource.class));

            assertEquals(one.getHost(), other.getHost());
            assertEquals(one.getPort(), other.getPort());
            assertNotEquals(one.getPath(), other.getPath());
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
