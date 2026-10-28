package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link ColumnTypeCheck} reports the column types outside the convention and nothing else:
 * {@code fixture/types/migration/V1__column_type_violations.sql}, in the schema "SAMPLE_TYPES". Flyway's
 * history table, with its {@code varchar} and {@code timestamp} columns, must not be reported.
 */
class ColumnTypeCheckTest {

    private static final String SCHEMA = "SAMPLE_TYPES";

    @Test
    void reportsExactlyTheFixturesWrongColumns() {
        DataSource dataSource = PostgresTestContainer.newDataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations("classpath:tr/girgin/backend/trading/analysis/platform/library/persistence"
                        + "/fixture/types/migration")
                .load()
                .migrate();

        List<String> violations = ColumnTypeCheck.violations(dataSource, SCHEMA);

        assertEquals(
                List.of(
                        "column \"BLOB\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": oid, use BYTEA (byte[])",
                        "column \"CLOCK\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": time with time zone,"
                                + " use TIME (LocalTime)",
                        "column \"CODE\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": character, use TEXT (String)",
                        "column \"COUNTER\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\":"
                                + " sequence default, the core makes the ID",
                        "column \"DOC\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\":"
                                + " xml, not a column type of backend-java-persistence.md",
                        "column \"NAME\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": character varying,"
                                + " use TEXT (String)",
                        "column \"PAYLOAD\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": json, use JSONB",
                        "column \"PRICE\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": money,"
                                + " use NUMERIC (BigDecimal)",
                        "column \"RATE\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": double precision,"
                                + " use NUMERIC (BigDecimal)",
                        "column \"RATIO\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": real,"
                                + " use NUMERIC (BigDecimal)",
                        "column \"REF\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": uuid," + " use TEXT (an ID wrapper)",
                        "column \"SEQ\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\":"
                                + " identity column, the core makes the ID",
                        "column \"SMALL\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": smallint," + " use INTEGER (int)",
                        "column \"STAMP\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": timestamp without time zone,"
                                + " use TIMESTAMPTZ (Instant)",
                        "column \"STAMPS\" of table \"SAMPLE_TYPES\".\"TYPES_WRONG\": timestamp without time zone[],"
                                + " use TIMESTAMPTZ (Instant)"),
                violations);
    }
}
