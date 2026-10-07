# Backend: database naming

Every database object of the platform's PostgreSQL database (tables, columns, indexes, constraints)
has an uppercase name, stored that way. PostgreSQL folds unquoted names to lower case, so every name
is quoted wherever it appears: in the migrations, in the SQL of the `Jdbc*Adapter` classes and in the
SQL of the tests.

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

- **Keywords and functions** are uppercase and not quoted: `SELECT`, `ORDER BY`, `LOWER(...)`,
  `EXCLUDED`.
- **Named parameters** keep their Java camelCase names (`:createdAt`); they are not database objects.
- **Row records** keep their camelCase component names. Spring's row mappers match a result column
  such as `CREATED_AT` to `createdAt` ignoring case and underscores.
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

A query and an upsert in an adapter (`JdbcAnalysisRepositoryAdapter`, `JdbcPresetRepositoryAdapter`):

```java
jdbc.sql("SELECT * FROM \"ANALYSES\"" + where + " ORDER BY \"CREATED_AT\" DESC LIMIT " + LIST_LIMIT)

jdbc.sql("""
        INSERT INTO "PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT")
        VALUES (:id, :name, :payload, :updatedAt)
        ON CONFLICT ("ID") DO UPDATE SET "NAME" = EXCLUDED."NAME", "PAYLOAD" = EXCLUDED."PAYLOAD",
            "UPDATED_AT" = EXCLUDED."UPDATED_AT"
        """)
```

By hand in `psql`, quote the names too:

```sql
SELECT * FROM "ANALYSES";
```

## Where it is checked

| Where | What it catches |
|---|---|
| The repository tests and the Spring Boot tests, on PostgreSQL through Testcontainers (`./gradlew :backend:test`, part of `mise run build`) | A statement whose quoting does not match the schema: a name left unquoted is folded to lower case and does not exist |
| Review | The stored names themselves: uppercase, explicit constraint and index names following the table above. An automated check of the catalog is planned in [#115](https://github.com/ramazangirgin/trading-analysis-platform/issues/115); until it lands, the reviewer checks every new migration |

## Why

One recognisable style for every database object, the same in migrations, adapter SQL and `psql`,
and fixed constraint and index names in the catalog instead of generated ones. It was the
developer's decision on #113. The cost: every name is quoted in every statement, and anyone writing
SQL by hand has to know it.
