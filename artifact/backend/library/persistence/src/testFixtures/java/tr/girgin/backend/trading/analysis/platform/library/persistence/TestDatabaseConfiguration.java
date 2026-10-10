package tr.girgin.backend.trading.analysis.platform.library.persistence;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The database of a test context: a PostgreSQL container bean marked {@code @ServiceConnection}. Spring Boot turns it
 * into the {@code JdbcConnectionDetails} of the context, {@code DataSourceAutoConfiguration} builds the Hikari pool
 * from them, as in production, and the container is stopped when the context closes. Every context has a container
 * of its own, so test classes never see each other's rows and the migration tests' seeded Flyway histories cannot
 * clash. The price is a container start per context. Not annotated {@code @Configuration}, only imported (by
 * {@code @JpaAdapterTest} and the application tests): the application's component scan covers this package, and the
 * application tests have the test fixtures on their classpath.
 */
public class TestDatabaseConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(PostgresTestContainer.IMAGE);
    }
}
