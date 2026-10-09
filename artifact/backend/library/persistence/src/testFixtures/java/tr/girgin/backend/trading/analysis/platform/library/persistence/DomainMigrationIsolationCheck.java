package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

/**
 * Migrates one domain alone on a database of its own and reports every object that is not in the
 * domain's schema.
 *
 * <p>Flyway's schema (history table and search path) is a decoy, {@value #DECOY}, so an unqualified
 * {@code CREATE} lands there and is reported, and an unqualified reference to the domain's own objects
 * fails the migration. The database holds no other domain's schema, so a reference to one fails the
 * migration too; such a failure is a violation that carries Flyway's message. The catalog then lists
 * every table, view, sequence, type and function outside the domain's schema, and every schema that
 * should not exist.
 */
final class DomainMigrationIsolationCheck {

    private static final String DECOY = "MIGRATION_CHECK";
    private static final String HISTORY_TABLE = "FLYWAY_SCHEMA_HISTORY";

    private static final String USER_SCHEMA = "n.nspname NOT LIKE 'pg\\_%' AND n.nspname <> 'information_schema'";

    private static final String OBJECTS = """
            SELECT CASE c.relkind WHEN 'r' THEN 'table' WHEN 'p' THEN 'table' WHEN 'v' THEN 'view'
                       WHEN 'm' THEN 'materialized view' WHEN 'S' THEN 'sequence' ELSE 'foreign table' END,
                   n.nspname::text, c.relname::text
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f') AND %1$s
            UNION ALL
            SELECT CASE t.typtype WHEN 'e' THEN 'enum type' WHEN 'd' THEN 'domain type'
                       WHEN 'c' THEN 'composite type' WHEN 'r' THEN 'range type'
                       WHEN 'm' THEN 'multirange type' ELSE 'type' END,
                   n.nspname::text, t.typname::text
            FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
            WHERE %1$s AND (t.typtype IN ('e', 'd', 'r', 'm')
                 OR (t.typtype = 'c' AND EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = t.typrelid AND c.relkind = 'c'))
                 OR (t.typtype = 'b' AND t.typcategory <> 'A'))
            UNION ALL
            SELECT CASE p.prokind WHEN 'p' THEN 'procedure' ELSE 'function' END, n.nspname::text, p.proname::text
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE %1$s
            UNION ALL
            SELECT 'schema', n.nspname::text, n.nspname::text FROM pg_namespace n WHERE %1$s
            """.formatted(USER_SCHEMA);

    private DomainMigrationIsolationCheck() {}

    /**
     * The violations of the domain's migrations, one readable line each, sorted. {@code domainFlyway}
     * is the domain's own Flyway (only its configuration is used: locations and settings), {@code schema}
     * the schema the domain owns.
     */
    static List<String> violations(Flyway domainFlyway, String schema) {
        DataSource dataSource = PostgresTestDatabase.create().dataSource();
        CatalogQueries.execute(dataSource, "CREATE SCHEMA \"%s\"".formatted(schema));
        Flyway isolated = Flyway.configure()
                .configuration(domainFlyway.getConfiguration())
                .dataSource(dataSource)
                .schemas(DECOY)
                .createSchemas(true)
                .load();
        try {
            isolated.migrate();
        } catch (FlywayException e) {
            return List.of(
                    "migration failed: " + String.join(" ", e.getMessage().split("\\s+")));
        }
        return catalogViolations(dataSource, schema);
    }

    private static List<String> catalogViolations(DataSource dataSource, String schema) {
        Set<String> violations = new TreeSet<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String[] row : CatalogQueries.query(connection, OBJECTS, 3)) {
                String kind = row[0];
                String where = row[1];
                String name = row[2];
                if ("schema".equals(kind)) {
                    if (!Set.of(schema, DECOY, "public").contains(name)) {
                        violations.add("schema \"%s\": a domain may only use its own schema".formatted(name));
                    }
                } else if (!isAllowed(kind, where, name, schema)) {
                    violations.add("%s \"%s\".\"%s\": outside the schema \"%s\"".formatted(kind, where, name, schema));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database catalog", e);
        }
        return List.copyOf(violations);
    }

    private static boolean isAllowed(String kind, String where, String name, String schema) {
        boolean history = DECOY.equals(where) && "table".equals(kind) && HISTORY_TABLE.equals(name);
        return schema.equals(where) || history;
    }
}
