package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link DatabaseNamingCheck} fails on names that break the convention and passes on names that
 * keep it: {@code fixture/naming/migration/V1__naming_violations.sql}, in the schema "SAMPLE_NAMING".
 */
class DatabaseNamingCheckTest {

    private static final String SCHEMA = "SAMPLE_NAMING";

    @Test
    void reportsExactlyTheFixturesViolations() {
        DataSource dataSource = PostgresTestContainer.newDataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations("classpath:tr/girgin/backend/trading/analysis/platform/library/persistence"
                        + "/fixture/naming/migration")
                .load()
                .migrate();

        DatabaseNamingCheck.Result result = DatabaseNamingCheck.run(dataSource, SCHEMA);

        assertEquals(
                List.of(
                        "column \"id\" of table \"SAMPLE_NAMING\".\"orders\": not uppercase",
                        "column \"label\" of table \"SAMPLE_NAMING\".\"NAMING_LOWER_COLUMN\": not uppercase",
                        "constraint \"NAMING_GENERATED_CODE_key\" on table \"SAMPLE_NAMING\".\"NAMING_GENERATED\""
                                + ": not uppercase",
                        "constraint \"NAMING_GENERATED_PARENT_ID_fkey\""
                                + " on table \"SAMPLE_NAMING\".\"NAMING_GENERATED\": not uppercase",
                        "constraint \"NAMING_GENERATED_pkey\" on table \"SAMPLE_NAMING\".\"NAMING_GENERATED\""
                                + ": not uppercase",
                        "constraint \"NAMING_WRONG_CODE_FK\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_PARENT_ID_FK",
                        "constraint \"NAMING_WRONG_CODE_UQ\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_UK",
                        "constraint \"NAMING_WRONG_KEY\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_PK",
                        "constraint \"NAMING_WRONG_LABEL_CHECK\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": no naming rule for this kind of constraint yet,"
                                + " start with backend-database-naming.md",
                        "enum type \"SAMPLE_NAMING\".\"mood\": not uppercase",
                        "index \"NAMING_GENERATED_KIND_IDX\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_IDX",
                        "index \"NAMING_GENERATED_LABEL_idx\" on table \"SAMPLE_NAMING\".\"NAMING_GENERATED\""
                                + ": not uppercase",
                        "index \"NAMING_WRONG_LABEL_IDX\" on table \"SAMPLE_NAMING\".\"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_UK",
                        "table \"SAMPLE_NAMING\".\"orders\": not uppercase"),
                result.violations());
    }

    @Test
    void reportsASchemaNameThatIsNotUppercase() {
        DataSource dataSource = PostgresTestContainer.newDataSource();
        CatalogQueries.execute(dataSource, "CREATE SCHEMA lower_case");

        DatabaseNamingCheck.Result result = DatabaseNamingCheck.run(dataSource, "lower_case");

        assertEquals(List.of("schema \"lower_case\": not uppercase"), result.violations());
    }
}
