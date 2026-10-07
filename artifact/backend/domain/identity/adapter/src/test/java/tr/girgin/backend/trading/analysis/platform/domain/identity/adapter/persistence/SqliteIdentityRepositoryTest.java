package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SqliteIdentityRepositoryTest.Config.class)
class SqliteIdentityRepositoryTest extends IdentityRepositoryContractTest {

    /** Spring has no error codes for SQLite, so a unique violation is not told apart from other failures. */
    @Override
    Class<? extends DataAccessException> duplicateUsernameException() {
        return UncategorizedSQLException.class;
    }

    @Configuration
    static class Config extends BaseConfig {

        @Bean
        DataSource dataSource() throws Exception {
            Path db = Files.createTempFile("identity", ".db");
            // foreign_keys=true like the application's datasource URL: SQLite does not enforce them otherwise.
            SingleConnectionDataSource dataSource =
                    new SingleConnectionDataSource("jdbc:sqlite:" + db + "?foreign_keys=true", true);
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
