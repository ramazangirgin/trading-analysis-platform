package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link DomainMigrationIsolationCheck} fails on migrations that leave their schema, for a made-up
 * domain whose schema is "SAMPLE" (fixtures under {@code fixture/<name>/migration}).
 */
class DomainMigrationIsolationCheckTest {

    private static final String SCHEMA = "SAMPLE";

    private static List<String> violationsOf(String fixture) {
        Flyway flyway = Flyway.configure()
                .schemas(SCHEMA)
                .createSchemas(true)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations("classpath:tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/"
                        + fixture + "/migration")
                .load();
        return DomainMigrationIsolationCheck.violations(flyway, SCHEMA);
    }

    @Test
    void reportsEveryObjectOutsideTheDomainSchema() {
        assertEquals(
                List.of(
                        "enum type \"MIGRATION_CHECK\".\"STRAY_MOOD\": outside the schema \"SAMPLE\"",
                        "function \"public\".\"STRAY_PUBLIC_FUNCTION\": outside the schema \"SAMPLE\"",
                        "schema \"STRAY_SCHEMA\": a domain may only use its own schema",
                        "sequence \"public\".\"STRAY_PUBLIC_SEQ\": outside the schema \"SAMPLE\"",
                        "table \"MIGRATION_CHECK\".\"STRAY_UNQUALIFIED\": outside the schema \"SAMPLE\"",
                        "table \"STRAY_SCHEMA\".\"STRAY_OWN\": outside the schema \"SAMPLE\"",
                        "table \"public\".\"STRAY_PUBLIC\": outside the schema \"SAMPLE\""),
                violationsOf("stray"));
    }

    @Test
    void failsAForeignKeyIntoAnotherDomainsSchema() {
        List<String> violations = violationsOf("cross");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("migration failed: ").contains("OTHER");
    }

    @Test
    void failsAColumnOfAnotherDomainsType() {
        List<String> violations = violationsOf("crosstype");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("migration failed: ").contains("OTHER");
    }

    @Test
    void passesACorrectDomain() {
        assertEquals(List.of(), violationsOf("sample"));
    }
}
