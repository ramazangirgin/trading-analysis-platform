package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The schema rules of one domain's persistence adapter, see docs/coding-convention/backend-java-persistence.md
 * ("One schema per domain"). The adapter extends this class once, in a test in its persistence package, and
 * names its configuration class, its schema and the method of its configuration that builds its Flyway:
 *
 * <pre>{@code
 * class AnalysisPersistenceConventionsTest extends DomainPersistenceConventionsTest {
 *     AnalysisPersistenceConventionsTest() {
 *         super(AnalysisPersistenceConfiguration.class, AnalysisPersistenceConfiguration.SCHEMA,
 *                 dataSource -> new AnalysisPersistenceConfiguration().analysisFlyway(dataSource));
 *     }
 * }
 * }</pre>
 *
 * The checks run against the domain's real Flyway, not a copy of its settings. Needs Docker.
 */
public abstract class DomainPersistenceConventionsTest {

    private final Class<?> configuration;
    private final String schema;
    private final Function<DataSource, Flyway> flywayBean;

    protected DomainPersistenceConventionsTest(
            Class<?> configuration, String schema, Function<DataSource, Flyway> flywayBean) {
        this.configuration = configuration;
        this.schema = schema;
        this.flywayBean = flywayBean;
    }

    /** Which classes count as the domain's: main classes only. A test of the checks themselves widens it. */
    protected ImportOption importOption() {
        return new ImportOption.DoNotIncludeTests();
    }

    @Test
    void entitiesAreMappedToTheDomainSchema() {
        var classes =
                new ClassFileImporter().withImportOption(importOption()).importPackages(configuration.getPackageName());

        assertEquals(List.of(), EntitySchemaRule.violations(classes, schema), "entities outside the schema " + schema);
    }

    @Test
    void migrationsStayInTheDomainSchema() {
        Flyway flyway = flywayBean.apply(PostgresTestDatabase.create().dataSource());

        assertEquals(
                List.of(),
                DomainMigrationIsolationCheck.violations(flyway, schema),
                "migration objects outside the schema " + schema);
    }

    @Test
    void theDomainFlywayCreatesTheSchemaWithItsHistory() throws SQLException {
        DataSource dataSource = PostgresTestDatabase.create().dataSource();

        flywayBean.apply(dataSource).migrate();

        try (Connection connection = dataSource.getConnection()) {
            List<String> inSchema = tables(connection, schema);
            assertTrue(inSchema.contains("FLYWAY_SCHEMA_HISTORY"), "no history table in " + schema + ": " + inSchema);
            assertTrue(inSchema.size() > 1, "the migrations found no table for " + schema + ", is the location right?");
            assertFalse(tables(connection, "public").contains("FLYWAY_SCHEMA_HISTORY"), "a history table is in public");
        }
    }

    @Test
    void namesFollowTheConvention() {
        DataSource dataSource = PostgresTestDatabase.create().dataSource();

        flywayBean.apply(dataSource).migrate();

        DatabaseNamingCheck.Result result = DatabaseNamingCheck.run(dataSource, schema);
        assertTrue(result.tables().size() > 1, "the check read no table of " + schema + ": " + result.tables());
        assertEquals(List.of(), result.violations(), "database names that break backend-database-naming.md");
    }

    private static List<String> tables(Connection connection, String schema) throws SQLException {
        return CatalogQueries.query(
                        connection,
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                        1,
                        schema)
                .stream()
                .map(row -> row[0])
                .toList();
    }
}
