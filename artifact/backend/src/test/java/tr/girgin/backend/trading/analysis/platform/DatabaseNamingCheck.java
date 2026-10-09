package tr.girgin.backend.trading.analysis.platform;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Reads the PostgreSQL catalog of schema {@code public} and reports every name that breaks
 * docs/coding-convention/backend-database-naming.md. Plain JDBC: the catalog has no entities.
 * NOT NULL constraints and everything inside Flyway's history table are exempt (see the document).
 */
final class DatabaseNamingCheck {

    private static final Pattern NAME = Pattern.compile("[A-Z0-9_]+");
    private static final String HISTORY_TABLE = "FLYWAY_SCHEMA_HISTORY";
    private static final String NOT_UPPERCASE = "not uppercase";

    private static final String TABLES = """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """;

    private static final String COLUMNS = """
            SELECT c.table_name, c.column_name FROM information_schema.columns c
            JOIN information_schema.tables t
              ON t.table_schema = c.table_schema AND t.table_name = c.table_name
            WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'
            """;

    private static final String CONSTRAINTS = """
            SELECT t.relname::text, k.conname::text, k.contype::text,
                   COALESCE((SELECT string_agg(a.attname::text, '_' ORDER BY u.ord)
                             FROM unnest(k.conkey) WITH ORDINALITY u (attnum, ord)
                             JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = u.attnum), '')
            FROM pg_constraint k
            JOIN pg_class t ON t.oid = k.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public' AND k.contype <> 'n'
            """;

    // An index that backs a primary key, unique or exclusion constraint is checked as that constraint.
    private static final String INDEXES = """
            SELECT t.relname::text, i.relname::text,
                   CASE WHEN x.indisunique THEN 'UNIQUE' ELSE 'PLAIN' END
            FROM pg_index x
            JOIN pg_class i ON i.oid = x.indexrelid
            JOIN pg_class t ON t.oid = x.indrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND NOT EXISTS (SELECT 1 FROM pg_constraint k
                              WHERE k.conindid = x.indexrelid AND k.conrelid = x.indrelid
                                AND k.contype IN ('p', 'u', 'x'))
            """;

    private static final String ENUM_TYPES = """
            SELECT t.typname::text FROM pg_type t
            JOIN pg_namespace n ON n.oid = t.typnamespace
            WHERE n.nspname = 'public' AND t.typtype = 'e'
            """;

    private DatabaseNamingCheck() {}

    static Result run(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> tables = new TreeSet<>();
            Set<String> violations = new TreeSet<>();
            for (String[] row : query(connection, TABLES, 1)) {
                tables.add(row[0]);
                report(
                        violations,
                        "table \"%s\"".formatted(row[0]),
                        NAME.matcher(row[0]).matches() ? null : NOT_UPPERCASE);
            }
            for (String[] row : query(connection, COLUMNS, 2)) {
                if (!HISTORY_TABLE.equals(row[0])) {
                    report(violations, "column \"%s\" of table \"%s\"".formatted(row[1], row[0]), upperCase(row[1]));
                }
            }
            for (String[] row : query(connection, CONSTRAINTS, 4)) {
                if (!HISTORY_TABLE.equals(row[0])) {
                    report(
                            violations,
                            "constraint \"%s\" on table \"%s\"".formatted(row[1], row[0]),
                            constraintProblem(row[0], row[1], row[2], row[3]));
                }
            }
            for (String[] row : query(connection, INDEXES, 3)) {
                if (!HISTORY_TABLE.equals(row[0])) {
                    report(
                            violations,
                            "index \"%s\" on table \"%s\"".formatted(row[1], row[0]),
                            indexProblem(row[0], row[1], "UNIQUE".equals(row[2])));
                }
            }
            for (String[] row : query(connection, ENUM_TYPES, 1)) {
                report(violations, "enum type \"%s\"".formatted(row[0]), upperCase(row[0]));
            }
            return new Result(tables, List.copyOf(violations));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database catalog", e);
        }
    }

    private static void report(Set<String> violations, String object, String problem) {
        if (problem != null) {
            violations.add(object + ": " + problem);
        }
    }

    private static String upperCase(String name) {
        return NAME.matcher(name).matches() ? null : NOT_UPPERCASE;
    }

    private static String constraintProblem(String table, String name, String type, String columns) {
        if (upperCase(name) != null) {
            return NOT_UPPERCASE;
        }
        return switch (type) {
            case "p" -> exactly(name, table + "_PK");
            case "f" -> exactly(name, table + "_" + columns + "_FK");
            case "u" -> shaped(table, name, "_UK");
            default -> "no naming rule for this kind of constraint yet, start with backend-database-naming.md";
        };
    }

    private static String indexProblem(String table, String name, boolean unique) {
        if (upperCase(name) != null) {
            return NOT_UPPERCASE;
        }
        return shaped(table, name, unique ? "_UK" : "_IDX");
    }

    private static String exactly(String name, String expected) {
        return name.equals(expected) ? null : "expected " + expected;
    }

    private static String shaped(String table, String name, String suffix) {
        boolean matches = name.startsWith(table + "_")
                && name.endsWith(suffix)
                && name.length() > table.length() + 1 + suffix.length();
        return matches ? null : "expected %s_<COLUMNS>%s".formatted(table, suffix);
    }

    private static List<String[]> query(Connection connection, String sql, int columns) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = resultSet.getString(i + 1);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** What the check read (the table names) and the violations, one readable line each, sorted. */
    record Result(Set<String> tables, List<String> violations) {}
}
