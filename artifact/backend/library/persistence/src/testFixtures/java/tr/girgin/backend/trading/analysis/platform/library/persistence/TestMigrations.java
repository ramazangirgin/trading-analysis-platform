package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/** Runs the Flyway migrations of one adapter module on a test database. */
public final class TestMigrations {

    private static final String DEFAULT_LOCATION = "classpath:db/migration";

    private TestMigrations() {}

    /**
     * Migrates with the history table the application uses. {@code baselineVersion} is the last
     * version that belongs to other modules, so only this module's own migrations apply; null for no
     * baseline. {@code extraLocations} hold test-only migrations (old-form rows for a migration
     * test) that run between the real versions.
     */
    public static void migrate(DataSource dataSource, String baselineVersion, String... extraLocations) {
        List<String> locations = new ArrayList<>(List.of(DEFAULT_LOCATION));
        locations.addAll(List.of(extraLocations));
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations(locations.toArray(String[]::new));
        if (baselineVersion != null) {
            configuration.baselineVersion(baselineVersion).baselineOnMigrate(true);
        }
        configuration.load().migrate();
    }
}
