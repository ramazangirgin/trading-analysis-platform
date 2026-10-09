package tr.girgin.backend.trading.analysis.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestMigrations;

/**
 * Proves {@link DatabaseNamingCheck} fails on names that break the convention and passes on names that
 * keep it: every real migration plus {@code db/naming-violations/V900__naming_violations.sql}.
 */
class DatabaseNamingCheckTest {

    @Test
    void reportsExactlyTheFixturesViolationsAndNoneOfTheRealMigrations() {
        DataSource dataSource = PostgresTestDatabase.create().dataSource();
        TestMigrations.migrate(dataSource, null, "classpath:db/naming-violations");

        DatabaseNamingCheck.Result result = DatabaseNamingCheck.run(dataSource);

        assertEquals(
                List.of(
                        "column \"id\" of table \"orders\": not uppercase",
                        "column \"ticker\" of table \"NAMING_LOWER_COLUMN\": not uppercase",
                        "constraint \"NAMING_GENERATED_ANALYSIS_ID_fkey\" on table \"NAMING_GENERATED\": not uppercase",
                        "constraint \"NAMING_GENERATED_CODE_key\" on table \"NAMING_GENERATED\": not uppercase",
                        "constraint \"NAMING_GENERATED_pkey\" on table \"NAMING_GENERATED\": not uppercase",
                        "constraint \"NAMING_WRONG_CODE_FK\" on table \"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_ANALYSIS_ID_FK",
                        "constraint \"NAMING_WRONG_CODE_UQ\" on table \"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_UK",
                        "constraint \"NAMING_WRONG_KEY\" on table \"NAMING_WRONG\": expected NAMING_WRONG_PK",
                        "constraint \"NAMING_WRONG_TICKER_CHECK\" on table \"NAMING_WRONG\""
                                + ": no naming rule for this kind of constraint yet,"
                                + " start with backend-database-naming.md",
                        "enum type \"mood\": not uppercase",
                        "index \"NAMING_GENERATED_KIND_IDX\" on table \"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_IDX",
                        "index \"NAMING_GENERATED_TICKER_idx\" on table \"NAMING_GENERATED\": not uppercase",
                        "index \"NAMING_WRONG_TICKER_IDX\" on table \"NAMING_WRONG\""
                                + ": expected NAMING_WRONG_<COLUMNS>_UK",
                        "table \"orders\": not uppercase"),
                result.violations());
    }
}
