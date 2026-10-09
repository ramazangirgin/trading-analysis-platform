# Plan: one database schema per domain, enforced

- **Issue**: #133 (Persistence: one database schema per domain (ANALYSIS, SETTINGS, IDENTITY), enforced and documented)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md: "the database without a migration". Existing databases are reset, not migrated)

## Goal

Each domain keeps its tables, enum types, constraints and indexes in a PostgreSQL schema of its own
(`"ANALYSIS"`, `"SETTINGS"`, `"IDENTITY"`). Each domain also owns its migrations, its Flyway bean and
its own history table, with versions starting at `V1`. Nothing of the application stays in `public`,
and no central class knows the list of domains. Each domain's persistence adapter proves its own
schema rules by extending abstract tests from `:backend:library:persistence`: its entities are
mapped to its schema, its migrations create everything in its schema and nothing outside it, name
every object with that schema and reference no other domain, and its names follow the naming
convention. The coding conventions describe the rule and the checks. Existing databases are dropped
and recreated once.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: migrations, entities, Flyway wiring | `backend-database-naming.md` (uppercase quoted names, named constraints and indexes, enum types named after the Java enum, migration comments), `backend-java-persistence.md` (Flyway owns the schema, `ddl-auto=validate`, entities in `domain.<d>.adapter.<port>.entity`, enum and enum-array mapping with `@ColumnTransformer`, no plain SQL in production code) |
| Backend: package placement | `backend-java-package-structure.md`: what a `domain.<d>.adapter.<port>` root may hold (`port_package_roots_hold_only_adapters`), adapter components implement an outbound port (`adapter_components_implement_an_outbound_port`). Both get a stated exception for the domain's persistence configuration |
| Backend: `:backend:library:persistence` | "Shared libraries": technical code only, no domain type, depends on libraries only; `repository-dependency-hygiene.md`: declare exactly the libraries used |
| Backend: tests and checks | `backend-java-persistence.md` "Tests" (Testcontainers, `@JpaAdapterTest`, no `org.springframework.jdbc`; the catalog checks are the JDBC exception, as `DatabaseNamingCheck` is today); `docs/coding-convention/README.md` "Changing a convention" (rule, check and document together, each rule shown failing on a deliberate violation) |
| Docs | Plain English, in the existing style; `README.md` gets an "Upgrading to 3.0.0" section like the 1.0.0 one |

## Design

### The domain's persistence configuration

Each domain with persistence gets one configuration class in its persistence adapter's root package,
next to the adapters: `AnalysisPersistenceConfiguration` (`domain.analysis.adapter.persistence`),
`SettingsPersistenceConfiguration`, `IdentityPersistenceConfiguration`. It is the one place of the
domain that names its schema:

- `public static final String SCHEMA = "ANALYSIS";` (`"SETTINGS"`, `"IDENTITY"`), used by the
  entities and the Flyway bean.
- `@Bean(initMethod = "migrate") Flyway analysisFlyway(DataSource dataSource)`
  (`settingsFlyway`, `identityFlyway`): the domain configures its Flyway itself, in a few lines, with
  no shared helper:
  - `Flyway.configure().dataSource(dataSource)`;
  - `.schemas(SCHEMA)`: the domain schema is Flyway's default schema, so it holds the history table
    `"<D>"."FLYWAY_SCHEMA_HISTORY"`;
  - `.createSchemas(true)`: **Flyway creates the schema**, no `CREATE SCHEMA` in any migration (the
    schema must exist before `V1` anyway, since the history table lives in it);
  - `.table("FLYWAY_SCHEMA_HISTORY")`;
  - `.locations("classpath:" + <the configuration class's package as a path> + "/migration")`
    (see below);
  - `.load()`.

  The bean names differ per domain, so the three beans live side by side.

**Spring Boot's Flyway auto-configuration backs off on its own**: its `Flyway` bean and its
`FlywayMigrationInitializer` are only created when there is no `Flyway` bean, and now there are
three. Hence `initMethod = "migrate"`: each bean migrates its domain when it is created. The starter
stays: its database-initializer detection (`FlywayDatabaseInitializerDetector`) treats every `Flyway`
bean as a database initializer, and Boot makes the `EntityManagerFactory` depend on them, so
Hibernate validates only after every domain is migrated. `TradingPlatformApplicationTests` proves
the order on an empty database (`ddl-auto=validate` fails otherwise). If the adapter tests' JPA
slice lacks that detection, the slice (`@JpaAdapterTest`) imports Boot's Flyway auto-configuration
so it is wired as in the application; the domain configuration does not change for tests.
`spring.flyway.table` leaves `application.properties`: it configured Boot's single `Flyway`, which no
longer exists. Each adapter module declares `flyway-core` as `implementation`.

### Migrations: in the domain's own package

Each domain's migrations live in its persistence adapter, under the configuration class's package:

```
domain/analysis/adapter/src/main/resources/
  tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/migration/
    V1__create_analyses.sql
```

Why not `db/migration` in each adapter module: the application puts every adapter jar on one
classpath, so `classpath:db/migration` would return every domain's files to every domain's Flyway.
The package path is unique to the domain by construction and needs no extra domain folder. The
configuration class derives the location from its own package
(`AnalysisPersistenceConfiguration.class.getPackageName().replace('.', '/')`), so it is never
written by hand. File names are `V<n>__<change>.sql`, versions start at `V1` in each domain.

Every name in a migration that can be schema-qualified is: `CREATE TABLE "ANALYSIS"."ANALYSES"`,
`CREATE TYPE "ANALYSIS"."ANALYST"`, `"ANALYSIS"."ANALYST"[]` as a column type,
`REFERENCES "IDENTITY"."USERS" ("ID")` inside identity, `COMMENT ON TYPE "ANALYSIS"."RATING"`,
`CREATE INDEX "ANALYSES_STATUS_IDX" ON "ANALYSIS"."ANALYSES" (...)`. PostgreSQL allows no qualified
name for an index or a constraint: they live in their table's schema.

The rewritten migrations (no data migration, so the final shape is created directly):

| Domain | Migration | Content |
|---|---|---|
| analysis | `…/domain/analysis/adapter/persistence/migration/V1__create_analyses.sql` | The five enum types of today's V4 (`ANALYSIS_STATUS`, `ANALYSIS_SOURCE`, `ASSET_TYPE`, `RATING`, `ANALYST`) with their `COMMENT ON TYPE`, then `ANALYSES` with the enum columns typed directly (`"ANALYSTS" "ANALYSIS"."ANALYST"[] NOT NULL`), the same columns, comments, primary key and four indexes as V1 + V4 today |
| settings | `…/domain/settings/adapter/persistence/migration/V1__create_presets.sql` | `PRESETS` as in today's V2 |
| identity | `…/domain/identity/adapter/persistence/migration/V1__create_users_and_roles.sql` | The `PERMISSION` enum type, `USERS` with `USERS_USERNAME_LOWER_UK`, `ROLES` with `"PERMISSIONS" "IDENTITY"."PERMISSION"[] NOT NULL DEFAULT '{}'`, `USER_ROLES` with its foreign keys and index. No `ROLE_PERMISSIONS` table |

Column names and order stay as today, so the entities change only in their schema.

### Entities: `schema = ` stays, from the domain's constant

JPA and Hibernate have no per-package default schema. The alternatives were checked and rejected:

- `hibernate.default_schema` is one value for the whole persistence unit; per domain it would mean a
  persistence unit, `EntityManagerFactory` and transaction manager per domain.
- An `orm.xml` per domain (`<entity-mappings><schema>`) applies only to the entities listed in that
  file, so it trades one annotation attribute for an XML file listing every entity.
- A custom Hibernate naming strategy does not see the entity's package when it names a schema; a
  metadata contributor that rewrites schemas would be hidden magic.

So every entity keeps `schema`, but never as a literal: `@Table(name = "ANALYSES", schema =
AnalysisPersistenceConfiguration.SCHEMA)`, likewise `@CollectionTable(name = "USER_ROLES", schema =
IdentityPersistenceConfiguration.SCHEMA, …)`. The schema name is written once per domain, and the
ArchUnit rule below fails an entity without it. The enum types in `columnDefinition` and the
`@ColumnTransformer` casts are written qualified, `"\"ANALYSIS\".\"ANALYST\"[]"`, as a plain string
like today (a constant concatenated into an escaped string would only be harder to read); the rule
checks them too. The cast must be qualified: at run time the search path is `"$user", public`, where
an unqualified `"ANALYST"` no longer exists, so the first insert would fail.

| Class | Change |
|---|---|
| `AnalysisEntity` | `@Table(… schema = AnalysisPersistenceConfiguration.SCHEMA)`; `"\"ANALYSIS\".\"ANALYSIS_STATUS\""`, `…ANALYSIS_SOURCE`, `…RATING` |
| `AnalysisSpecEmbeddable` | `"\"ANALYSIS\".\"ASSET_TYPE\""`; `cast(? as \"ANALYSIS\".\"ANALYST\"[])` and `"\"ANALYSIS\".\"ANALYST\"[]"` |
| `PresetEntity` | `@Table(… schema = SettingsPersistenceConfiguration.SCHEMA)` |
| `UserEntity` | `@Table(… schema = IdentityPersistenceConfiguration.SCHEMA)`; `@CollectionTable(… schema = IdentityPersistenceConfiguration.SCHEMA)` |
| `RoleEntity` | `@Table(… schema = IdentityPersistenceConfiguration.SCHEMA)`; `"IDENTITY"."PERMISSION"[]` in the cast and the column definition |

### Architecture rules that change

- `PersistenceArchitectureTest.production_code_does_not_use_plain_sql`: `javax.sql.DataSource` is
  allowed in the `*PersistenceConfiguration` classes, which only hand it to Flyway. `java.sql`, the rest of `javax.sql` and `org.springframework.jdbc` stay banned everywhere.
- `ArchitectureTest.port_package_roots_hold_only_adapters` and
  `adapter_components_implement_an_outbound_port`: a `@Configuration` named
  `<Domain>PersistenceConfiguration` is allowed in `domain.<d>.adapter.persistence`, and nowhere else
  in a domain adapter (a new clause in the placement rules).

### Adapter tests use the domain's own wiring

The adapter tests' component scan already covers `adapter.persistence`, so it picks up the domain's
configuration: its Flyway bean migrates the test database before Hibernate validates, exactly as in
the application. The test `Config` classes only provide the data source
(`PostgresTestDatabase.create().dataSource()`). `TestMigrations` goes away, with `baselineVersion`.
So do the two migration tests and their seeds, with the migrations they tested:
`V4AnalysisEnumTypesMigrationTest`, `V5IdentityPermissionArrayMigrationTest`,
`db/seed/analysis/V1_1__analysis_seed_old_rows.sql`, `db/seed/identity/V3_1__identity_seed_old_rows.sql`.

### Enforcement: abstract tests in the library, one subclass per domain

No central check. `:backend:library:persistence` test fixtures provide the checks and one abstract
JUnit class; each domain's adapter module extends it once.

**`DomainPersistenceConventionsTest`** (abstract, test fixtures). A subclass, in the domain's
`adapter.persistence` test package, names its configuration class, its schema and its Flyway bean
method, so the test checks the domain's real Flyway configuration and no copy of it:

```java
class AnalysisPersistenceConventionsTest extends DomainPersistenceConventionsTest {
    AnalysisPersistenceConventionsTest() {
        super(AnalysisPersistenceConfiguration.class, AnalysisPersistenceConfiguration.SCHEMA,
                dataSource -> new AnalysisPersistenceConfiguration().analysisFlyway(dataSource));
    }
}
```

For the isolation check it copies that configuration
(`Flyway.configure().configuration(flyway.getConfiguration())`) and changes only the schemas.

Its tests, each delegating to a check class in the test fixtures:

1. **`entitiesAreMappedToTheDomainSchema`** (`EntitySchemaRule`, ArchUnit): imports the domain's
   adapter package (the configuration class's package and below, main classes only) and asserts:
   every `@Entity` has `@Table` with `schema` equal to the domain's; every `@CollectionTable` and
   `@JoinTable` on a field or method too; every quoted name in a `columnDefinition` and in a
   `@ColumnTransformer` `write` is qualified with `"<D>".` (`"ANALYST"[]` and
   `"IDENTITY"."PERMISSION"[]` in analysis both fail). It also fails when the package holds no entity,
   so a wrong package cannot pass silently.
2. **`migrationsStayInTheDomainSchema`** (`DomainMigrationIsolationCheck`): on a fresh database of its
   own (`PostgresTestDatabase.create()`), migrates the domain alone, with Flyway's default schema
   (history table and search path) set to a decoy schema `"MIGRATION_CHECK"` and the domain schema
   second. Then:
   - an unqualified `CREATE` lands in the decoy and is reported; an unqualified reference to the
     domain's own type or table fails the migration;
   - any reference to another domain (a foreign key to `"IDENTITY"."USERS"`, a column of type
     `"ANALYSIS"."RATING"` in settings) fails the migration: that schema does not exist in this
     database. A failed migration is a violation carrying Flyway's message;
   - from the catalog: every table, view, materialized view, sequence, foreign table, type (enum,
     domain, composite; not the array and row types PostgreSQL derives) and function or procedure
     lies in `"<D>"`, except the history table in the decoy; no other schema exists apart from the
     system ones and an empty `public`. This also catches an object created in `public` or in a
     schema the migration made up.

   This is the plan's pick for the issue's "migrations touch only their own schema". A static SQL
   check cannot tell a table name from a column, constraint or index name, which are never
   qualified. A role per domain would not catch an unqualified name, since Flyway sets the search
   path to the domain schema.
3. **`theDomainFlywayCreatesTheSchemaWithItsHistory`**: migrates with the domain's Flyway bean as
   it is, and asserts the schema exists with `FLYWAY_SCHEMA_HISTORY` in it, holds at least one table
   (so a wrong location, which would find no migration, fails), and `public` holds no history table.
4. **`namesFollowTheConvention`** (`DatabaseNamingCheck`, moved from `artifact/backend` tests into
   the test fixtures): on that database, reads the domain schema, not `public`. It now also reports a
   schema name that is not uppercase, and names the schema in every line, e.g.
   `table "ANALYSIS"."orders": not uppercase`.

Between them they cover every item of the issue's database test: objects outside the domain
schema, a foreign key into another schema, and a column type from another schema all fail in the
domain whose migration wrote them.

**Proof that each check fails** (library's own `src/test`, against fixtures of a made-up domain
`sample` under `tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/…`, never
on a production classpath):

| Test | Fixture | Asserts |
|---|---|---|
| `EntitySchemaRuleTest` | Fixture entities: one without `@Table`, one with another domain's schema, one without a schema, a `@CollectionTable` without a schema, an unqualified `columnDefinition` type, an unqualified cast; one correct | The exact violations, none for the correct one |
| `DomainMigrationIsolationCheckTest` | `…/fixture/stray/migration/V1__stray.sql`: an unqualified table and type, a table, a sequence and a function in `public`, a schema of its own with a table; a correct qualified table. `…/fixture/cross/migration/V1__cross.sql`: a foreign key to `"IDENTITY"."USERS"`. `…/fixture/crosstype/migration/V1__cross_type.sql`: a column of type `"ANALYSIS"."ANALYSIS_STATUS"` | The exact violations of `stray`; the migration-failed violation for `cross` and `crosstype` |
| `DatabaseNamingCheckTest` (moved) | Today's `V900__naming_violations.sql`, rewritten into the fixture domain's schema with qualified names, plus a lower-case schema | The updated exact list |
| `DomainPersistenceConventionsTest` itself | A `SamplePersistenceConventionsTest` over a correct fixture domain | All four tests pass, so the abstract class works end to end |

`artifact/backend` keeps no schema check of its own: `DatabaseNamingTest`, `DatabaseNamingCheck`,
`DatabaseNamingCheckTest` and `src/test/resources/db/naming-violations/` move to the library or go
away. Its Spring Boot tests still start the whole application on an empty database, which proves the
three domains migrate side by side.

**Every domain with persistence must have its conventions test: a Gradle check.** A new task
`domainPersistenceTestsCheck` in `artifact/backend/build.gradle.kts` goes through every
`:backend:domain:<d>:adapter` project. A domain has persistence when its main sources hold an `@Entity`
or its resources a `**/adapter/persistence/migration/*.sql`. Such a domain must have a test class in
`src/test/java` that `extends DomainPersistenceConventionsTest`. Otherwise the task fails and names
each domain and what is missing, e.g. `:backend:domain:catalog:adapter has persistence (@Entity in
CatalogEntity.java) but no test extending DomainPersistenceConventionsTest`. It is a static text scan
of the source files, with the scanned folders as declared task inputs (up to date while nothing
changes, configuration-cache safe). `:backend:check` depends on it, so `mise run build` and CI run
it, and `mise run check` names it too. It is proved by hand once (remove a domain's conventions test,
run the task, see it fail) and that run goes in the pull request's description, like the changed
ArchUnit rules

### Local database, Compose, deployment

Nothing in the setup changes: one database `platform`, one user. `scripts/postgres.sh`, the Compose
file and `deploy/smoke-test.sh` need no change (the smoke test starts on an empty database). The repo
has no Ansible or Terraform yet (#76). An existing database has to be recreated once: on the old one
the platform would start, create the three schemas next to the old tables in `public` and ignore
them, so the README says to reset it (`mise run db:reset` locally, the `postgres/` folder for
Compose), as for 1.0.0.

## Work packages

### WP1: The abstract conventions test

- **Depends on**: none
- **Status**: done
- **Files**:
  - `artifact/backend/library/persistence/src/testFixtures/java/…/library/persistence/DomainPersistenceConventionsTest.java`, `EntitySchemaRule.java`, `DomainMigrationIsolationCheck.java`, `DatabaseNamingCheck.java` (new; the last moved from `artifact/backend/src/test`)
  - delete `…/testFixtures/…/TestMigrations.java`
  - `artifact/backend/library/persistence/src/test/java/…/library/persistence/`: `EntitySchemaRuleTest`, `DomainMigrationIsolationCheckTest`, `DatabaseNamingCheckTest`, `SamplePersistenceConventionsTest` and the fixture entities and configuration (new)
  - `artifact/backend/library/persistence/src/test/resources/tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/…` (fixture migrations, new)
  - `artifact/backend/library/persistence/build.gradle.kts`, `gradle/libs.versions.toml` (if a library alias is missing)
- **Steps**:
  - [x] The three checks and the abstract test as in the design. The fixtures need JUnit, ArchUnit and
        Flyway as `testFixturesApi`; JDBC (`java.sql`) is fine in test fixtures.
  - [x] The proofs and fixtures in the library's own tests.
  - [x] Declared dependencies match (`analyzeDependencies` in `mise run check`).
- **Tests**: `EntitySchemaRuleTest`, `DomainMigrationIsolationCheckTest`, `DatabaseNamingCheckTest`,
  `SamplePersistenceConventionsTest`.

### WP2: Each domain's configuration, migrations, entities and tests

- **Depends on**: WP1
- **Status**: done
- **Files** (per domain `<d>` in analysis, settings, identity):
  - `domain/<d>/adapter/src/main/java/…/domain/<d>/adapter/persistence/<D>PersistenceConfiguration.java` (new)
  - `domain/<d>/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/<d>/adapter/persistence/migration/V1__….sql` (new); delete `domain/<d>/adapter/src/main/resources/db/migration/*`
  - entities: `AnalysisEntity`, `AnalysisSpecEmbeddable`, `PresetEntity`, `UserEntity`, `RoleEntity`
  - `domain/<d>/adapter/src/test/java/…/persistence/<D>PersistenceConventionsTest.java` (new)
  - test configs: `AdapterTestSupport`, `JpaPresetRepositoryAdapterTest`, `JpaIdentityRepositoryTest`;
    delete `V4AnalysisEnumTypesMigrationTest`, `V5IdentityPermissionArrayMigrationTest` and both seed files
  - `domain/<d>/adapter/build.gradle.kts` (`flyway-core`, the library's test fixtures if not yet there)
- **Steps**:
  - [x] The configuration class with `SCHEMA` and the domain's Flyway bean.
  - [x] The `V1` migration with every qualifiable name qualified, a header saying it belongs to the
        domain's schema and that versions are per domain, and the comments of the old migrations.
  - [x] Entities as in the design table.
  - [x] Test configs provide only the data source; drop the `TestMigrations` calls and the
        "V1 belongs to another module" comments.
  - [x] The domain's conventions test.
- **Tests**: `<D>PersistenceConventionsTest` per domain; the existing repository tests
  (`JpaAnalysisRepositoryAdapterTest` and the other tests on `AdapterTestSupport`,
  `JpaPresetRepositoryAdapterTest`, `JpaIdentityRepositoryTest`) pass with unchanged assertions on
  the new schema with `ddl-auto=validate`, and inserting an analysis and a role proves the qualified
  casts.

### WP3: Application wiring and architecture rules

- **Depends on**: WP2
- **Status**: done
- **Files**:
  - `artifact/backend/src/main/resources/application.properties` (drop `spring.flyway.table`, comment where Flyway is configured now)
  - `artifact/backend/src/test/java/…/platform/ArchitectureTest.java`, `PersistenceArchitectureTest.java`
  - `artifact/backend/build.gradle.kts` (the `domainPersistenceTestsCheck` task), `mise.toml` (`check` runs it; its description says so)
  - delete `artifact/backend/src/test/java/…/platform/DatabaseNamingTest.java`, `DatabaseNamingCheckTest.java`, `DatabaseNamingCheck.java` (moved in WP1), `artifact/backend/src/test/resources/db/naming-violations/`
- **Steps**:
  - [x] The exceptions of the design's "Architecture rules that change", each with its reason in the
        `because`, and a rule that a `@Configuration` in a domain adapter is a
        `<Domain>PersistenceConfiguration` in `adapter.persistence`.
  - [x] `domainPersistenceTestsCheck` as in the design, a dependency of `:backend:check`, and in `mise run check`.
  - [x] Remove `spring.flyway.table`; if `@JpaAdapterTest` needs it, import Boot's Flyway auto-configuration there (design).
- **Tests**: `ArchitectureTest`, `PersistenceArchitectureTest` on the real code;
  `TradingPlatformApplicationTests` and `AnalysesApiIntegrationTest` start the application on an empty
  database (all three Flyway beans run before Hibernate validates). Show once, by hand, that each
  changed rule still fails on a misplaced class (a `@Configuration` in another adapter package, a
  `DataSource` field in an adapter), as `docs/coding-convention/README.md` asks; no committed fixture.

### WP4: Documentation and version

- **Depends on**: WP1, WP2, WP3
- **Status**: done
- **Files**: `docs/coding-convention/backend-database-naming.md`, `docs/coding-convention/backend-java-persistence.md`,
  `docs/coding-convention/backend-java-package-structure.md`, `docs/coding-convention/README.md`, `README.md`,
  `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] The documents as in "Docs to update".
  - [x] `mise run version:bump major` (2.7.0 → 3.0.0, or the next major if `main` moved).
- **Tests**: `mise run check`, `mise run build`.

## Tests

- Library (`:backend:library:persistence`): the proofs that each check fails
  (`EntitySchemaRuleTest`, `DomainMigrationIsolationCheckTest`, `DatabaseNamingCheckTest`,
  `SamplePersistenceConventionsTest`).
- Per domain: `AnalysisPersistenceConventionsTest`, `SettingsPersistenceConventionsTest`,
  `IdentityPersistenceConventionsTest`, and the repository tests on the domain's own wiring.
- Application: `ArchitectureTest`, `PersistenceArchitectureTest`, the Spring Boot tests on an empty
  database, and `domainPersistenceTestsCheck` (every domain with persistence has its conventions test).
- The end-to-end tests and the Compose smoke test run the jar on an empty database, which proves the
  migration locations resolve inside the Boot jar.

How the issue's acceptance is proved:

| Acceptance | Proof |
|---|---|
| `\dn` shows the three schemas, each with its own `FLYWAY_SCHEMA_HISTORY`; `public` holds nothing of the application | Each domain's `theDomainFlywayCreatesTheSchemaWithItsHistory` and `migrationsStayInTheDomainSchema`; the Spring Boot tests run all three together |
| Starts on an empty database, `mise run build` green | The Spring Boot tests, e2e, smoke test, CI |
| A foreign key from `ANALYSIS` to `IDENTITY."USERS"` fails the build | `AnalysisPersistenceConventionsTest.migrationsStayInTheDomainSchema` (the schema does not exist in isolation); the `cross` fixture |
| A table in `public` fails the build | `migrationsStayInTheDomainSchema`; the `stray` fixture |
| An entity without the right `schema` fails the build | `entitiesAreMappedToTheDomainSchema`; `EntitySchemaRuleTest` |
| An unqualified object in a domain migration fails the build | `migrationsStayInTheDomainSchema` (it lands in the decoy schema); the `stray` fixture |
| The conventions describe the rule and its enforcement | WP4 |

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-database-naming.md` | Schemas: one per domain, the uppercase domain name, quoted; nothing in `public`. "Not renamed" keeps the database and the user only. Migrations: in the persistence adapter's package (`…/adapter/persistence/migration/`), `V<n>__<change>.sql`, versions per domain from `V1`, Flyway creates the schema, every qualifiable name qualified (indexes and constraints cannot be). History table: one per schema, set by the domain's Flyway bean. Examples with qualified names and the entity's `schema = …SCHEMA`; `SELECT * FROM "ANALYSIS"."ANALYSES"`; links to the new files. "Where it is checked": the naming check runs per domain, in the domain's conventions test, and checks the schema name |
| Text | `docs/coding-convention/backend-java-persistence.md` | New section "One schema per domain": the boundary; no foreign key, view, type or function across schemas; other domains referenced by plain ID; cross-domain logic in `orchestration`; the `<Domain>PersistenceConfiguration` (schema constant, the domain's own Flyway bean, why Boot's auto-configuration backs off and Hibernate still waits for it); why `schema =` stays on entities (the rejected alternatives); why the migrations live under the package. Update the mapping table and the enum-array paragraph (qualified types). Rules: the `DataSource` exception. "Shared persistence code": the conventions test and its checks in the fixtures, `TestMigrations` gone. "Tests": adapter tests migrate through the domain's own Flyway bean; a migration test seeds with an extra location of its own (generic, no named example). "Where it is checked": the abstract test and its four checks, the proofs in the library, and `domainPersistenceTestsCheck`, which fails a domain with persistence but no conventions test. A checklist "Giving a domain persistence": configuration class, migration folder, conventions test |
| Text | `docs/coding-convention/backend-java-package-structure.md` | Next to "domains are independent": each domain's tables live in its own database schema, the database side of the package boundary, link to the persistence document. The package layout and placement table: `<Domain>PersistenceConfiguration` in `adapter.persistence`, the migrations under it. The shared-libraries row of `:backend:library:persistence`: the conventions test in the fixtures |
| Text | `docs/coding-convention/README.md` | The "Database naming" row of the enforcement table becomes "Database naming and schemas": the per-domain conventions tests, the library's proofs and `domainPersistenceTestsCheck` (also in `mise run check`); the removed tests no longer listed |
| Text | `README.md` | New "Upgrading to 3.0.0" above "Upgrading to 2.0.0": each domain's tables move into a schema of their own, so the database is reset, not migrated (same reset steps as 1.0.0 for Compose; `mise run db:reset` for local runs; runs the platform started, presets and users are lost, data-folder analyses are imported again). The backup paragraph's hand query becomes `SELECT * FROM "ANALYSIS"."ANALYSES";`. The CI table's *Backend and frontend* row: "the database naming and schema checks of every domain (uppercase names, explicit constraint and index names, everything in the domain's own schema, a conventions test in every domain with persistence)". The task list's `mise run check` line, if it lists what the check runs |
| Screenshot | none | No page changes |

## Out of scope

- Migrating existing data (the issue's decision): no migration from the old `public` tables, and no
  startup guard against an old database; the README's upgrade section covers the reset.
- A database role or datasource per domain, and splitting domains into separate databases.
- Ansible / Terraform: none in the repository yet (#76).
