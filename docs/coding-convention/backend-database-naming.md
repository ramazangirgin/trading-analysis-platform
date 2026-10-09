# Backend: database naming

Every database object of the platform's PostgreSQL database (tables, columns, indexes, constraints,
enum types) has an uppercase name, stored that way. PostgreSQL folds unquoted names to lower case, so
every name is quoted wherever it appears: in the migrations, in the entity mappings (written plain in
`@Table` / `@Column`, quoted by Hibernate) and by hand in `psql`. The code has no SQL of its own
([persistence](backend-java-persistence.md)).

## Rules

- **Names**: uppercase letters, digits and underscores (`ANALYSES`, `CREATED_AT`, `ROLE_PERMISSIONS`).
  Always quoted: `"ANALYSES"`, never `analyses` or `ANALYSES`, which PostgreSQL would read as
  `analyses`.
- **Constraints and indexes have explicit names**, so the catalog never holds a name PostgreSQL
  generated in lower case. Inline `PRIMARY KEY`, `UNIQUE` and `REFERENCES` are written as named
  `CONSTRAINT` clauses:

  | Object | Name |
  |---|---|
  | Primary key | `<TABLE>_PK` |
  | Foreign key | `<TABLE>_<COLUMN>_FK` |
  | Unique constraint or unique index | `<TABLE>_<COLUMNS>_UK` |
  | Index | `<TABLE>_<COLUMNS>_IDX` |

- **Two exemptions**, where the name is not ours to choose:
  - A `NOT NULL` constraint keeps the name PostgreSQL 18 gives it (`<TABLE>_<COLUMN>_not_null`). It is
    a column property, written as `NOT NULL` in the column definition and never as a named
    constraint.
  - Flyway's history table has our name, but its columns, primary key and index are created by Flyway
    in lower case and cannot be configured.
- **Enum types** are named in upper snake case after the Java enum they mirror (`AnalysisStatus` →
  `"ANALYSIS_STATUS"`). Their labels are the Java constant names (`'ANALYSIS_READ_ALL'`), in
  declaration order, and the migration comments which enum the type mirrors. A set of enum values is
  an array of the type: `"ANALYST"[]`. No enum is stored as `TEXT`.
- **Keywords and functions** are uppercase and not quoted: `SELECT`, `ORDER BY`, `LOWER(...)`,
  `EXCLUDED`.
- **Entity mappings** name every table and column explicitly, unquoted and uppercase:
  `@Table(name = "ANALYSES")`, `@Column(name = "CREATED_AT")`. `globally_quoted_identifiers=true`
  quotes them, and the standard physical naming strategy keeps their case (Spring Boot's default
  strategy would lower-case them). An enum column also names its type,
  `columnDefinition = "\"ANALYSIS_STATUS\""`, quoted by hand: Hibernate does not quote a column
  definition.
- **Flyway's history table** is `FLYWAY_SCHEMA_HISTORY` (`spring.flyway.table` in
  `application.properties`, `.table(...)` in every test that configures Flyway).
- **Not renamed**: the database, the user and the schema (`platform`, `platform`, `public`), which the
  Compose file, the deploy setup and `pg_dump` commands name.
- **In Java**, a text block needs no escaping; a one-line string escapes the quotes (`\"`). A
  statement built from pieces quotes the names in every piece.

## Migrations

- One Flyway migration per domain change, in `db/migration` of the domain's adapter module.
- Versions are global across domains, in order of creation (`V1` analysis, `V2` settings, `V3`
  identity, and so on), named `V<n>__<domain>_<change>.sql`.
- Never edit a migration that has been applied (merged into `main`): add a new one.
- Each table and column that is not obvious gets a comment in the migration.

## Examples

A table with named constraints, and indexes
([`V3__identity_create_users_and_roles.sql`](../../artifact/backend/domain/identity/adapter/src/main/resources/db/migration/V3__identity_create_users_and_roles.sql)):

```sql
CREATE TABLE "USER_ROLES" (
    "USER_ID" TEXT NOT NULL,
    "ROLE_ID" TEXT NOT NULL,
    CONSTRAINT "USER_ROLES_PK" PRIMARY KEY ("USER_ID", "ROLE_ID"),
    CONSTRAINT "USER_ROLES_USER_ID_FK" FOREIGN KEY ("USER_ID") REFERENCES "USERS" ("ID") ON DELETE CASCADE,
    CONSTRAINT "USER_ROLES_ROLE_ID_FK" FOREIGN KEY ("ROLE_ID") REFERENCES "ROLES" ("ID")
);

CREATE INDEX "USER_ROLES_ROLE_ID_IDX" ON "USER_ROLES" ("ROLE_ID");
CREATE UNIQUE INDEX "USERS_USERNAME_LOWER_UK" ON "USERS" (LOWER("USERNAME"));
```

An enum type and an array of one
([`V4__analysis_enum_types.sql`](../../artifact/backend/domain/analysis/adapter/src/main/resources/db/migration/V4__analysis_enum_types.sql),
[`V5__identity_permission_array.sql`](../../artifact/backend/domain/identity/adapter/src/main/resources/db/migration/V5__identity_permission_array.sql)):

```sql
CREATE TYPE "ANALYSIS_STATUS" AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED');
ALTER TABLE "ANALYSES" ALTER COLUMN "STATUS" TYPE "ANALYSIS_STATUS" USING "STATUS"::"ANALYSIS_STATUS";

ALTER TABLE "ROLES" ADD COLUMN "PERMISSIONS" "PERMISSION"[] NOT NULL DEFAULT '{}';
```

The same names in an entity (`AnalysisEntity`):

```java
@Entity
@Table(name = "ANALYSES")
public class AnalysisEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "STATUS", nullable = false, columnDefinition = "\"ANALYSIS_STATUS\"")
    private AnalysisStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
```

By hand in `psql`, quote the names too:

```sql
SELECT * FROM "ANALYSES";
```

## Where it is checked

| Where | What it catches |
|---|---|
| The repository tests and the Spring Boot tests, on PostgreSQL through Testcontainers, with `ddl-auto=validate` (`./gradlew :backend:test` and the adapter modules' tests, part of `mise run build`) | An entity whose table, column or type name does not match the schema fails at startup; a migration whose names are wrong fails its tests |
| [`DatabaseNamingTest`](../../artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/DatabaseNamingTest.java) (`./gradlew :backend:test`, part of `mise run build`; needs Docker) | The stored names after every migration of every domain: a table, column, constraint, index or enum type whose name is not uppercase, and a constraint or index that does not follow the four name patterns above (which is also how a name PostgreSQL generated shows up, e.g. `ANALYSES_pkey`). A failure lists every violation, one line each, e.g. `index "analyses_bad_idx" on table "ANALYSES": not uppercase`. A named check constraint is reported too: the convention has no name for it yet, so adding one starts with this document |
| [`DatabaseNamingCheckTest`](../../artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/DatabaseNamingCheckTest.java) | The check itself: it runs a test-only migration that breaks every rule (`src/test/resources/db/naming-violations`) and asserts the exact violations, plus one correct table that yields none |

## Why

One recognisable style for every database object, the same in migrations, entity mappings and `psql`,
and fixed constraint and index names in the catalog instead of generated ones. It was the
developer's decision on #113. The cost: every name is quoted in every statement, and anyone writing
SQL by hand has to know it.

The check reads the catalog, not the SQL, and there is no SQL linter. The code has no SQL of its own,
the catalog is what PostgreSQL actually stored, and one test covers every migration of every domain
without another tool in the hook.
