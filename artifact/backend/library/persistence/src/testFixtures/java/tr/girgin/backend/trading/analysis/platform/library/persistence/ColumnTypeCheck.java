package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;

/**
 * Reads the PostgreSQL catalog of one schema and reports every column whose type is outside the table of
 * docs/coding-convention/backend-java-persistence.md ("Mapping domain types"), and every identity or
 * sequence-backed column. Plain JDBC: the catalog has no entities. Flyway's history table is exempt, as in
 * {@link DatabaseNamingCheck}.
 */
final class ColumnTypeCheck {

    private static final String HISTORY_TABLE = "FLYWAY_SCHEMA_HISTORY";

    private static final Set<String> ALLOWED = Set.of(
            "text", "bool", "int4", "int8", "numeric", "date", "time", "timestamptz", "interval", "jsonb", "bytea");

    private static final Map<String, String> RULED_OUT = Map.of(
            "timestamp", "timestamp without time zone, use TIMESTAMPTZ (Instant)",
            "timetz", "time with time zone, use TIME (LocalTime)",
            "varchar", "character varying, use TEXT (String)",
            "bpchar", "character, use TEXT (String)",
            "float4", "real, use NUMERIC (BigDecimal)",
            "float8", "double precision, use NUMERIC (BigDecimal)",
            "money", "money, use NUMERIC (BigDecimal)",
            "int2", "smallint, use INTEGER (int)",
            "json", "json, use JSONB",
            "oid", "oid, use BYTEA (byte[])");

    private static final String UUID_TYPE = "uuid";

    // Arrays are typcategory 'A' and point at their element type; typtype 'e' is an enum.
    private static final String COLUMNS = """
            SELECT c.relname::text, a.attname::text, t.typname::text, t.typtype::text,
                   COALESCE(et.typname::text, ''), COALESCE(et.typtype::text, ''),
                   a.attidentity::text, COALESCE(pg_get_expr(d.adbin, d.adrelid), '')
            FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_type t ON t.oid = a.atttypid
            LEFT JOIN pg_type et ON et.oid = t.typelem AND t.typcategory = 'A'
            LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE n.nspname = ? AND c.relkind IN ('r', 'p') AND a.attnum > 0 AND NOT a.attisdropped
            """;

    private ColumnTypeCheck() {}

    /** One readable line per violation, sorted. */
    static List<String> violations(DataSource dataSource, String schema) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> violations = new TreeSet<>();
            for (String[] row : CatalogQueries.query(connection, COLUMNS, 8, schema)) {
                if (HISTORY_TABLE.equals(row[0])) {
                    continue;
                }
                String problem = problem(row);
                if (problem != null) {
                    violations.add(
                            "column \"%s\" of table \"%s\".\"%s\": %s".formatted(row[1], schema, row[0], problem));
                }
            }
            return List.copyOf(violations);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database catalog", e);
        }
    }

    private static String problem(String[] row) {
        if (!row[6].isEmpty()) {
            return "identity column, the core makes the ID";
        }
        if (row[7].contains("nextval(")) {
            return "sequence default, the core makes the ID";
        }
        boolean array = !row[4].isEmpty();
        String type = array ? row[4] : row[2];
        String typtype = array ? row[5] : row[3];
        if ("e".equals(typtype) || ALLOWED.contains(type)) {
            return null;
        }
        String suffix = array ? "[]" : "";
        if (UUID_TYPE.equals(type)) {
            return "uuid" + suffix + ", use TEXT (an ID wrapper)";
        }
        String known = RULED_OUT.get(type);
        return known != null
                ? known.replaceFirst(", use", suffix + ", use")
                : type + suffix + ", not a column type of backend-java-persistence.md";
    }
}
