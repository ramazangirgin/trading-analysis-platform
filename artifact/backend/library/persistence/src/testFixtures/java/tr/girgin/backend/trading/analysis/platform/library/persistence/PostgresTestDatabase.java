package tr.girgin.backend.trading.analysis.platform.library.persistence;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

/**
 * Temporary bridge for the adapter and application tests that still build their own data source; they move to
 * {@link TestDatabaseConfiguration} in the next work package of #141, which deletes this class.
 */
public final class PostgresTestDatabase {

    private PostgresTestDatabase() {}

    public static Database create() {
        JdbcConnectionDetails details = PostgresTestContainer.newDatabase();
        return new Database(details.getJdbcUrl(), details.getUsername(), details.getPassword());
    }

    /** The JDBC URL and credentials of one database. */
    public record Database(String url, String username, String password) {

        /** A data source for the database: one connection per use. */
        public DataSource dataSource() {
            return PostgresTestContainer.dataSource(new JdbcConnectionDetails() {
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
            });
        }
    }
}
