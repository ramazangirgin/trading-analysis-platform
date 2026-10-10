package tr.girgin.backend.trading.analysis.platform.library.persistence;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;

/**
 * The database of a test context: a {@link JdbcConnectionDetails} bean for a fresh database in the one container of
 * the test JVM (see {@link PostgresTestContainer}). {@code DataSourceAutoConfiguration} builds the Hikari pool from
 * it, as in production. One bean per context gives every context a database of its own, and a cached context keeps
 * it. Not annotated {@code @Configuration}, only imported (by {@code @JpaAdapterTest} and the application tests):
 * the application's component scan covers this package, and the application tests have the test fixtures on their
 * classpath.
 */
public class TestDatabaseConfiguration {

    @Bean
    JdbcConnectionDetails testDatabaseConnectionDetails() {
        return PostgresTestContainer.newDatabase();
    }
}
