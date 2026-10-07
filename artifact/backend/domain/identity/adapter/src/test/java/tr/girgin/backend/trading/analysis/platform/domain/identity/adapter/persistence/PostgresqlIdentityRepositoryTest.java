package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Needs Docker: skipped without it, run in CI's build job. The image is the Compose file's PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(PostgresqlIdentityRepositoryTest.Config.class)
class PostgresqlIdentityRepositoryTest extends IdentityRepositoryContractTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Override
    Class<? extends DataAccessException> duplicateUsernameException() {
        return DuplicateKeyException.class;
    }

    @Configuration
    static class Config extends BaseConfig {

        @Bean
        DataSource dataSource() {
            // The container is started by the Testcontainers extension before the Spring context loads.
            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            // Only this domain's migration: V1 to V4 belong to other modules.
            Flyway.configure()
                    .dataSource(dataSource)
                    .baselineVersion("4")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            return dataSource;
        }
    }
}
