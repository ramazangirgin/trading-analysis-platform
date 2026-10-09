# Backend: database naming

Every database object of the platform's PostgreSQL database (schemas, tables, columns, indexes,
constraints, enum types) has an uppercase name, stored that way. PostgreSQL folds unquoted names to
lower case, so every name is quoted wherever it appears: in the migrations, in the entity mappings
(written plain in `@Table` / `@Column`, quoted by Hibernate) and by hand in `psql`. The code has no
SQL of its own ([persistence](backend-java-persistence.md)).

## Rules

- **Schemas**: one per domain, named after it in uppercase and quoted: `"ANALYSIS"`, `"SETTINGS"`,
  `"IDENTITY"`. Everything a domain owns (tables, enum types, constraints, indexes) lives in its
  schema, and nothing of the application stays in `public`
  ([One schema per domain](backend-java-persistence.md#one-schema-per-domain)).
- **Names**: uppercase letters, digits and underscores (`ANALYSES`, `CREATED_AT`, `USER_ROLES`).
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
  - Flyway's history tables have our name, but their columns, primary key and index are created by
    Flyway in lower case and cannot be configured.
- **Enum types** are named in upper snake case after the Java enum they mirror (`AnalysisStatus` →
  `"ANALYSIS"."ANALYSIS_STATUS"`). Their labels are the Java constant names (`'ANALYSIS_READ_ALL'`),
  in declaration order, and the migration comments which enum the type mirrors. A set of enum values
  is an array of the type: `"ANALYSIS"."ANALYST"[]`. No enum is stored as `TEXT`.
- **Qualified names**: every name in a migration that can carry a schema does (tables, types, the
  table after `REFERENCES`, `COMMENT ON`, the table after `ON` in `CREATE INDEX`). PostgreSQL allows
  no qualified name for an index or a constraint: they live in their table's schema.
- **Keywords and functions** are uppercase and not quoted: `SELECT`, `ORDER BY`, `LOWER(...)`,
  `EXCLUDED`.
- **Entity mappings** name every table and column explicitly, unquoted and uppercase:
  `@Table(name = "ANALYSES", schema = AnalysisPersistenceConfiguration.SCHEMA)`,
  `@Column(name = "CREATED_AT")`. `globally_quoted_identifiers=true` quotes them, and the standard
  physical naming strategy keeps their case (Spring Boot's default strategy would lower-case them).
  An enum column also names its type, schema included,
  `columnDefinition = "\"ANALYSIS\".\"ANALYSIS_STATUS\""`, quoted by hand: Hibernate does not quote
  a column definition.
- **Flyway's history table** is `FLYWAY_SCHEMA_HISTORY`, one per schema
  (`"ANALYSIS"."FLYWAY_SCHEMA_HISTORY"`), set by the domain's own Flyway bean.
- **Not renamed**: the database and the user (`platform`, `platform`), which the Compose file, the
  deploy setup and `pg_dump` commands name.
- **In Java**, a text block needs no escaping; a one-line string escapes the quotes (`\"`). A
  statement built from pieces quotes the names in every piece.

## Migrations

- One Flyway migration per domain change, in the package of the domain's persistence configuration:
  `domain/<d>/adapter/src/main/resources/…/domain/<d>/adapter/persistence/migration/`. The package
  path is unique to the domain, so one domain's Flyway never finds another's files on the shared
  classpath (`classpath:db/migration` would).
- Named `V<n>__<change>.sql`. Versions are per domain and start at `V1` in each.
- Flyway creates the domain's schema (`createSchemas`); no migration has a `CREATE SCHEMA`.
- Every name that can be qualified is, and a migration references only its own domain's schema: no
  foreign key, type or view across domains.
- Never edit a migration that has been applied (merged into `main`): add a new one.
- Each table and column that is not obvious gets a comment in the migration.

## Examples

A table with named constraints, and indexes
([`V1__create_users_and_roles.sql`](../../artifact/backend/domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/V1__create_users_and_roles.sql)):

```sql
CREATE TABLE "IDENTITY"."USER_ROLES" (
    "USER_ID" TEXT NOT NULL,
    "ROLE_ID" TEXT NOT NULL,
    CONSTRAINT "USER_ROLES_PK" PRIMARY KEY ("USER_ID", "ROLE_ID"),
    CONSTRAINT "USER_ROLES_USER_ID_FK" FOREIGN KEY ("USER_ID") REFERENCES "IDENTITY"."USERS" ("ID") ON DELETE CASCADE,
    CONSTRAINT "USER_ROLES_ROLE_ID_FK" FOREIGN KEY ("ROLE_ID") REFERENCES "IDENTITY"."ROLES" ("ID")
);

CREATE INDEX "USER_ROLES_ROLE_ID_IDX" ON "IDENTITY"."USER_ROLES" ("ROLE_ID");
CREATE UNIQUE INDEX "USERS_USERNAME_LOWER_UK" ON "IDENTITY"."USERS" (LOWER("USERNAME"));
```

An enum type and an array of one
([`V1__create_analyses.sql`](../../artifact/backend/domain/analysis/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/migration/V1__create_analyses.sql),
[`V1__create_users_and_roles.sql`](../../artifact/backend/domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/V1__create_users_and_roles.sql)):

```sql
CREATE TYPE "ANALYSIS"."ANALYSIS_STATUS" AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED');

"PERMISSIONS" "IDENTITY"."PERMISSION"[] NOT NULL DEFAULT '{}'
```

The same names in an entity (`AnalysisEntity`):

```java
@Entity
@Table(name = "ANALYSES", schema = AnalysisPersistenceConfiguration.SCHEMA)
public class AnalysisEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "STATUS", nullable = false, columnDefinition = "\"ANALYSIS\".\"ANALYSIS_STATUS\"")
    private AnalysisStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
```

By hand in `psql`, quote the names too:

```sql
SELECT * FROM "ANALYSIS"."ANALYSES";
```

## Where it is checked

| Where | What it catches |
|---|---|
| The repository tests and the Spring Boot tests, on PostgreSQL through Testcontainers, with `ddl-auto=validate` (`./gradlew :backend:test` and the adapter modules' tests, part of `mise run build`) | An entity whose table, column or type name does not match the schema fails at startup; a migration whose names are wrong fails its tests |
| `namesFollowTheConvention` in each domain's `<D>PersistenceConventionsTest` (e.g. `AnalysisPersistenceConventionsTest`; `./gradlew :backend:domain:<d>:adapter:test`, part of `mise run build`; needs Docker) | The stored names after the domain's migrations, in the domain's schema: a schema, table, column, constraint, index or enum type whose name is not uppercase, and a constraint or index that does not follow the four name patterns above (which is also how a name PostgreSQL generated shows up, e.g. `ANALYSES_pkey`). A failure lists every violation, one line each, naming the schema, e.g. `index "ANALYSIS"."analyses_bad_idx" on table "ANALYSES": not uppercase`. A named check constraint is reported too: the convention has no name for it yet, so adding one starts with this document |
| `DatabaseNamingCheckTest` in `:backend:library:persistence` | The check itself: it runs a test-only migration that breaks every rule and asserts the exact violations, plus one correct table that yields none |
| The other checks of the conventions test and `domainPersistenceTestsCheck` ([persistence](backend-java-persistence.md#where-it-is-checked)) | Objects outside the domain's schema, unqualified names, references to another domain, and a domain with persistence but no conventions test |

## Why

One recognisable style for every database object, the same in migrations, entity mappings and `psql`,
and fixed constraint and index names in the catalog instead of generated ones. It was the
developer's decision on #113. The cost: every name is quoted in every statement, and anyone writing
SQL by hand has to know it.

The check reads the catalog, not the SQL, and there is no SQL linter. The code has no SQL of its own,
the catalog is what PostgreSQL actually stored, and every domain's own test covers its migrations
without another tool in the hook.
