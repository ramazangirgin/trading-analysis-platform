# Plan: PostgreSQL as the only database

- **Issue**: #113
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md: the
  database is reset with no migration, as the Flyway history is rewritten; the `postgres` profile,
  a configuration setting, goes away)

In this plan, "the embedded database" is the single-file database the platform used by default
until now (driver `org.xerial`, URL `jdbc:<embedded>:${platform.home}/platform.db`). The pattern
`sq[l]ite` (case-insensitive) finds every mention of it; it is written that way so this plan does
not match it itself.

Added on the developer's request after review round 1 (see "Changes after review round 1"):
`PLAN.md` is deleted, every database object name is written in uppercase, and the older plan files
in `.plans/` are never edited.

## Goal

PostgreSQL 18 is the platform's only database, and the repository has no code, comment, test,
configuration or document that mentions the embedded database any more. `mise run run` and
`mise run dev` start a local `postgres:18.6` container (Docker or Podman) on their own, or use the
developer's own server through the `PLATFORM_DB_*` variables. The end-to-end tests run the jar
against a throwaway PostgreSQL container, and every repository and Spring Boot test runs on
PostgreSQL through Testcontainers. The migrations are rewritten from scratch for PostgreSQL, with
its native types (`timestamptz`, `date`, `bigint`, `double precision`), so the database is reset:
an existing Docker Compose database and an old local `platform.db` file are not migrated. The README
and the pull request (release notes) say so and give the reset steps. This replaces the issue's
"upgrades with no manual step" criterion, by the developer's decision on the plan.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend | `docs/coding-convention/backend-java-package-structure.md`: test helpers stay in the test sources of the module that uses them, in the package of the code under test (`….domain.<d>.adapter.persistence`, `….domain.analysis.adapter`, `…` for `:backend`); no new Gradle module. Row records in `persistence/row/`, MapStruct mappers `*To*Mapper` in `persistence/mapper/`. `backend-java-checkstyle.md` and `backend-java-formatting.md` for every changed Java file (`mise run check`). Flyway: versions are global across domains, one file per domain change. Libraries come from the version catalog `gradle/libs.versions.toml`, versions from the Spring Boot BOM. |
| e2e | `e2e/start-platform.sh` stays the one entry point Playwright's `webServer` (and `mise run screenshots`) starts. |
| scripts, mise | Developer entry points are `mise` tasks in `mise.toml` with a `description`; shell scripts under `scripts/` with a header comment saying what they do and what they need, as `scripts/version.sh` does. Docker and Podman must both work, as in `deploy/`. |
| deploy | `deploy/docker-compose.yml` keeps its `PLATFORM_DB_*` variables; only the profile goes. |
| CI, Renovate | `.github/workflows/ci.yml` header comment lists what each job runs. `.github/renovate.json5`: the Testcontainers and script PostgreSQL images follow the Compose image (same group, no majors). |
| docs | `docs/coding-convention/repository-versioning-and-releases.md` (major bump). Docs in English, plain style. |

## Design

### One datasource configuration

- `application.properties` gets the PostgreSQL datasource that `application-postgres.properties`
  has today: `jdbc:postgresql://${PLATFORM_DB_HOST:localhost}:${PLATFORM_DB_PORT:5432}/${PLATFORM_DB_NAME:platform}`,
  `PLATFORM_DB_USER` (default `platform`), `PLATFORM_DB_PASSWORD` (default empty), Hikari pool 10.
  No `spring.flyway.locations`: Flyway's default `classpath:db/migration` is the only location.
- `application-postgres.properties` is deleted. `deploy/docker-compose.yml` drops
  `SPRING_PROFILES_ACTIVE: postgres`; its `PLATFORM_DB_HOST` / `PLATFORM_DB_PASSWORD` stay.
- `artifact/backend/build.gradle.kts`: `runtimeOnly(libs.postgresql)` and
  `runtimeOnly(libs.flyway.database.postgresql)` without the "postgres profile" comment. The
  `org.xerial` driver goes from every build file and from the version catalog.
- `TradingPlatformApplication.main` no longer creates the platform home (it did so only for the
  embedded database's file): the adapters that write there create their directories themselves
  (`JsonlEventStoreAdapter`, the runner adapters, `DotenvSecretStoreAdapter`). The `PlatformHome`
  helper goes with it, and any test of it.

### Migrations: a new history, PostgreSQL only (the database is reset)

Every existing migration (`V1`, `V3`, `V4`, and `V4_1` with the `db/migration-postgresql`
directory in the analysis adapter, `V2` in settings, `V5` in identity) is deleted and replaced by
one migration per domain, in the single location `db/migration`, written for PostgreSQL only.
Versions stay global across domains, in order of creation:

- `V1__analysis_create_analyses.sql` (analysis adapter): the `analyses` table in its final shape,
  with what V3, V4 and V4_1 added folded in: `external_ref` with its unique index, `runner_ref`.
  Types: `id`, enums and free text `TEXT`; `trade_date DATE`; `max_debate_rounds`,
  `max_risk_discuss_rounds` `INTEGER`; `llm_calls`, `tool_calls`, `tokens_in`, `tokens_out`,
  `elapsed_ms` `BIGINT NOT NULL DEFAULT 0`; `cost_usd DOUBLE PRECISION`; `checkpoint_enabled BOOLEAN`;
  `created_at TIMESTAMPTZ NOT NULL`, `started_at`, `ended_at` `TIMESTAMPTZ`. The same three indexes
  (`created_at`, `(ticker, trade_date)`, `status`). No data backfill (there is no data).
- `V2__settings_create_presets.sql` (settings adapter): `presets` with `updated_at TIMESTAMPTZ NOT NULL`;
  `payload` stays `TEXT` (the adapter treats it as an opaque JSON string; `jsonb` would reformat it).
- `V3__identity_create_users_and_roles.sql` (identity adapter): today's V5 with
  `locked_until TIMESTAMPTZ`, `created_at` / `updated_at TIMESTAMPTZ NOT NULL`; the unique index on
  `lower(username)`, the foreign keys and their cascades as today.

Comments in the new files say what each table and column holds, nothing about other databases.

**Existing databases.** Flyway's validation fails on a database migrated with the old history
(the checksums of V1 and V2 differ, V3 is not the script it applied, V4–V5 are gone), so the
platform refuses to start on it rather than mixing the two. It never cleans or drops anything
itself (`spring.flyway.clean-disabled` stays at its default, true). The reset is a documented
manual step:

- Docker Compose: `docker compose -f deploy/docker-compose.yml down`, optionally `pg_dump` first
  (the README already shows it), delete `${PLATFORM_DATA}/postgres`, `up -d`. Analyses that
  TradingAgents' CLI left in the data dir are imported again on startup; runs started by the
  platform, presets and users are lost.
- Local: `mise run db:reset` (see below) removes the local container and its volume; an old
  `~/.tradingagents-platform/platform.db` file can be deleted.

### Persistence adapters on the native types

The row records and mappers follow the new column types; the domain models (`Instant`,
`LocalDate`) do not change:

- `AnalysisRow`: `tradeDate` `LocalDate`; `createdAt`, `startedAt`, `endedAt` `OffsetDateTime`.
  `UserRow`: `lockedUntil`, `createdAt`, `updatedAt` `OffsetDateTime`. `PresetRow`: `updatedAt`
  `OffsetDateTime`. `OffsetDateTime` because the PostgreSQL driver reads and writes `timestamptz`
  as `OffsetDateTime` (`setObject` / `getObject`), not as `Instant`; written in UTC.
- Mappers, one per conversion in each adapter's `persistence/mapper/` package (`*To*Mapper`,
  MapStruct `@Mapper` interfaces with a `default` method as today):
  - analysis: `InstantToStringMapper` and `StringToTimestampMapper` are replaced by
    `InstantToOffsetDateTimeMapper` and `OffsetDateTimeToInstantMapper`; the trade date maps
    `LocalDate` to `LocalDate` with no mapper;
  - identity: `InstantToStringMapper`, `OptionalInstantToStringMapper`, `StringToInstantMapper`,
    `StringToOptionalInstantMapper` are replaced by `InstantToOffsetDateTimeMapper`,
    `OptionalInstantToOffsetDateTimeMapper`, `OffsetDateTimeToInstantMapper`,
    `OffsetDateTimeToOptionalInstantMapper`;
  - settings: `InstantToOffsetDateTimeMapper` and `OffsetDateTimeToInstantMapper`, used by
    `PresetToPresetRowMapper` and `PresetRowToPresetMapper`.
- The SQL in `JdbcAnalysisRepositoryAdapter`, `JdbcUserRepositoryAdapter`,
  `JdbcRoleRepositoryAdapter` and `JdbcPresetRepositoryAdapter` keeps its statements (`ON CONFLICT`
  and `lower()` are PostgreSQL). `ORDER BY created_at` now orders by time natively; comments about
  "fixed-width text so text order is time order" go.
- Precision: `timestamptz` keeps microseconds, the old text kept milliseconds. Round-trip
  assertions in the adapter tests keep passing; where a test builds an `Instant` with nanoseconds,
  it truncates it to microseconds.

### Comments and Javadoc that name the embedded database

Every one goes or is rewritten for PostgreSQL alone. Known ones:

- `Username`: lookups ignore case with PostgreSQL's `lower()`, whose result for non-ASCII letters
  depends on the database's locale; ASCII only keeps "which names are equal" independent of it.
  The rule itself does not change.
- `UserRepositoryPort.save`: a username taken by another user fails with Spring's
  `DuplicateKeyException` (no second exception type).
- `AdapterTestSupport`, `AnalysesApiIntegrationTest`: "a migrated PostgreSQL database".
- `application.properties`, `TradingPlatformApplication`, `build.gradle.kts` files: as above.

### Local PostgreSQL for `mise run run` and `mise run dev` (option b of the issue)

A new script `scripts/postgres.sh` (bash, `set -euo pipefail`), with the engine taken from
`CONTAINER_ENGINE`, else `docker` when it is on `PATH`, else `podman`:

- `start`: when `PLATFORM_DB_HOST` is set to anything other than `localhost` or `127.0.0.1`,
  prints that the developer's own server is used and exits 0 (option c). Otherwise starts the
  container `trading-analysis-platform-db` if it is not running (creates it the first time, starts
  it when it exists but is stopped): image `postgres:18.6`, named volume
  `trading-analysis-platform-db` on `/var/lib/postgresql`, port
  `127.0.0.1:${PLATFORM_DB_PORT:-5432}:5432`, `POSTGRES_DB=platform`, `POSTGRES_USER=platform`,
  `POSTGRES_HOST_AUTH_METHOD=trust` (the port is published on 127.0.0.1 only, so the application's
  default empty password works). Then waits up to 60 s for `pg_isready -U platform -d platform`
  inside the container, and fails with the container's last log lines otherwise.
- `stop`: stops the container; the volume keeps the data.
- `reset`: removes the container and its volume (the next `start` creates an empty database).
- `throwaway`: for the end-to-end tests. Starts a `--rm` container labelled
  `trading-analysis-platform.e2e=true` with a random host port (`-p 127.0.0.1::5432`), waits for
  `pg_isready`, and prints `<container id> <host port>` on stdout. Before starting, it removes
  leftover containers with that label (a test run killed with SIGKILL cannot clean up).

`mise.toml`:

- new task `db` ("Start the local PostgreSQL for run and dev (Docker or Podman), unless
  PLATFORM_DB_HOST points elsewhere"): `scripts/postgres.sh start`;
- new tasks `db:stop` and `db:reset`;
- `run` and `dev` get `depends = ["db"]`.

### End-to-end tests

`e2e/start-platform.sh` starts `scripts/postgres.sh throwaway`, exports `PLATFORM_DB_HOST=127.0.0.1`
and `PLATFORM_DB_PORT=<host port>`, and runs the jar in the background instead of `exec`: a trap on
`EXIT INT TERM` stops the jar and removes the container, and the script `wait`s for the jar so
Playwright's `webServer` still sees one long-running process. The throwaway platform home stays
(run directories, secrets file). The `e2e` and `screenshots` task descriptions say that Docker or
Podman is required.

### Tests on PostgreSQL (Testcontainers)

One container per test JVM (each Gradle module's tests run in their own JVM), one fresh database
per test class, so classes never see each other's rows and the build starts one container per
module instead of one per class:

- A test helper `PostgresTestDatabase` in the test sources of every module that needs one: the
  analysis adapter (`….domain.analysis.adapter`), the settings adapter
  (`….domain.settings.adapter.persistence`), the identity adapter
  (`….domain.identity.adapter.persistence`) and `:backend` (`…`). Each copy is the same small class
  (about 30 lines): a lazily started static `PostgreSQLContainer("postgres:18.6")` (Testcontainers
  stops it with the JVM through Ryuk), and `create()` that runs `CREATE DATABASE test_<n>` on it and
  returns its JDBC URL, user and password (a small record). Duplicated rather than shared through a
  new test-support module, which the package-structure convention does not have.
- Docker (or Podman) is required to run the backend tests: no `disabledWithoutDocker`, so a
  missing engine fails the build loudly instead of skipping every database test. The README's
  prerequisites say so; CI's runners have Docker.
- Analysis adapter: `AdapterTestSupport.Config.dataSource()` migrates a fresh PostgreSQL database.
  `build.gradle.kts`: `testRuntimeOnly(libs.postgresql)`,
  `testRuntimeOnly(libs.flyway.database.postgresql)`, `testImplementation(libs.testcontainers.postgresql)`
  instead of the `org.xerial` driver.
- Settings adapter: `JdbcPresetRepositoryAdapterTest.Config.dataSource()` the same way, keeping
  `baselineVersion("1")` (only this domain's V2 is on the module's classpath). Same build changes.
- Identity adapter: the embedded-database variant of the repository test (the class beside
  `IdentityRepositoryContractTest` and `PostgresqlIdentityRepositoryTest`) is deleted.
  `IdentityRepositoryContractTest` and `PostgresqlIdentityRepositoryTest` are merged into one
  concrete `JdbcIdentityRepositoryTest` (the contract's tests, `DuplicateKeyException` asserted
  directly, no `duplicateUsernameException()` hook), using `PostgresTestDatabase` instead of its own
  `@Container`, with `baselineVersion("2")` (only V3 is on the module's classpath).
  `testcontainers-junit-jupiter` is no longer needed there and is removed from the module and,
  when nothing else uses it, from the version catalog.
- `:backend`: `TestPlatformHome.register` also registers `spring.datasource.url`, `username` and
  `password` of a fresh `PostgresTestDatabase` per test class, so `TradingPlatformApplicationTests`
  and `AnalysesApiIntegrationTest` run on PostgreSQL. `build.gradle.kts`:
  `testImplementation(libs.testcontainers.postgresql)`.

### Renovate

The custom regex manager for the PostgreSQL image matches every `PostgresTestDatabase.java` and
`scripts/postgres.sh` (`postgres:(?<currentValue>[0-9][^"\s]*)`), instead of
`PostgresqlIdentityRepositoryTest.java`. It stays under the existing "PostgreSQL: no major updates"
rule, so the Compose, script and test images move together.

### No mention left anywhere

Beyond the backend, every tracked file is cleaned, with two exceptions:

- `README.md`: see "Docs to update". `PLAN.md` is deleted (below).
- **Exception 1**: the older plan files in `.plans/` (every one but this plan) are records of
  earlier work and are never edited; `.plans/79-identity-users-roles-tables.md` keeps its mentions.
  The pull request leaves every file in `.plans/` but this one exactly as on `main`.
- **Exception 2**: `artifact/ta-runner/uv.lock`, generated by `uv lock` from the pinned
  TradingAgents release, which depends on LangGraph's checkpoint package whose name contains the
  word. The repository cannot rename a third-party package; it is not the platform's database.

## Changes after review round 1 (developer's request)

### `PLAN.md` is deleted

The work is planned in GitHub issues now, so `PLAN.md` goes, and nothing may point at it any more.
Every reference (`PLAN.md`, `PLAN.md section <n>`, `PLAN.md D<n>`, `PLAN.md §<n>`) is removed from
code comments, configuration and docs; where the comment only pointed at the design, it now says
the reason itself in one short clause, or just drops the parenthesis when the comment is already
clear. No reference is replaced by a link to an issue unless the comment is about planned work
(then the open issue, e.g. authentication and HTTPS before exposing the platform beyond
localhost). Known places (`git grep -n "PLAN\.md"`, `.plans/` excepted):

- `README.md` line 7 ("See PLAN.md for the design and roadmap"): the roadmap is the repository's
  GitHub issues; link them instead. Line 222 ("planned, see PLAN.md §7"): name the plan without
  the link, or link the open issue for authentication and HTTPS if one exists
  (`gh issue list --search "auth"`); line 359 (the repository tree): drop `PLAN.md`.
- `artifact/backend/src/main/resources/application.properties` (3), `deploy/Dockerfile`,
  `deploy/docker-compose.yml`, `artifact/ta-runner/Dockerfile`, `artifact/ta-runner/pyproject.toml`.
- Backend Java: `ApiException`, `RunnerOutputLineParser`, `DockerRunnerAdapter`,
  `ProcessRunnerAdapter`, both `RunnerKind` classes, `AnalysisError`, `AnalysisLogService`,
  `AnalysisSpecValidator`, `FileSystemDataDirAdapter`, `JsonRunHistoryAdapter`,
  `SystemHealthService`, `ImportExistingRunsUseCase`.
- Frontend: `features/reports/model/reportLanguage.ts`, `shared/i18n/index.ts`.
- ta-runner: `ta_runner/catalog/catalog.py`, `ta_runner/engine/compat.py` (2),
  `tests/engine/test_upstream_contract.py`.
- Also check `docs/` and `.claude/` (skills) for references.

Check: `git grep -n "PLAN\.md" -- ':!.plans'` prints nothing (the older plans may mention it).

### Uppercase database object names, stored in uppercase (quoted)

Every database object is created and stored with an uppercase name: tables, columns, indexes,
constraints and the primary keys' and foreign keys' names. PostgreSQL folds unquoted names to
lower case, so every name is **quoted** wherever it appears, in the three migrations, in every
statement of the four `Jdbc*Adapter` classes and in every SQL string in the tests:

```sql
CREATE TABLE "ANALYSES" (
    "ID"         TEXT        NOT NULL,
    "TICKER"     TEXT        NOT NULL,
    "CREATED_AT" TIMESTAMPTZ NOT NULL,
    ...
    CONSTRAINT "ANALYSES_PK" PRIMARY KEY ("ID")
);
CREATE INDEX "ANALYSES_CREATED_AT_IDX" ON "ANALYSES" ("CREATED_AT");
CREATE UNIQUE INDEX "USERS_USERNAME_LOWER_UK" ON "USERS" (LOWER("USERNAME"));

SELECT * FROM "ANALYSES" WHERE "STATUS" IN (:statuses) ORDER BY "CREATED_AT" DESC
INSERT INTO "PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT") VALUES (:id, :name, :payload, :updatedAt)
    ON CONFLICT ("ID") DO UPDATE SET "NAME" = EXCLUDED."NAME", ...
```

- Constraints and indexes get explicit names, so no name is generated in lower case by PostgreSQL:
  `<TABLE>_PK`, `<TABLE>_<COLUMN>_FK`, `<TABLE>_<COLUMNS>_UK` (unique), `<TABLE>_<COLUMNS>_IDX`.
  Inline `PRIMARY KEY`, `UNIQUE` and `REFERENCES` become named `CONSTRAINT` clauses.
- SQL keywords and functions are uppercase and unquoted (`SELECT`, `LOWER`, `EXCLUDED`). Named
  parameters (`:createdAt`) keep their Java names.
- In Java text blocks the quotes need no escaping; in one-line strings they are escaped (`\"`).
  Where a statement is built from pieces (the `where` clause of the analysis list), every piece
  quotes its names.
- Reading rows: the result columns come back as `ID`, `CREATED_AT`, …; Spring's
  `SimplePropertyRowMapper` / `DataClassRowMapper` behind `JdbcClient.query(Row.class)` match
  column labels to record components case-insensitively with underscores removed, so the row
  records keep their names. A repository test per adapter proves every column round-trips.
- Flyway's history table is uppercase as well: `spring.flyway.table=FLYWAY_SCHEMA_HISTORY` in
  `application.properties`, and `.table("FLYWAY_SCHEMA_HISTORY")` in every test's
  `Flyway.configure()` (Flyway quotes the name itself).
- Not renamed: the database, the user and the schema (`platform`, `platform`, `public`), which
  the Compose file, the deploy setup and `pg_dump` commands name; and `CREATE DATABASE test_<n>`
  in `PostgresTestDatabase`.
- Anyone querying by hand quotes the names too (`SELECT * FROM "ANALYSES";`): the README's
  database notes say so in one line. #112 (Hibernate) must map the quoted names (explicit
  `@Table(name = "\"ANALYSES\"")` / `@Column` names, or `hibernate.globally_quoted_identifiers`);
  noted on #112 when this pull request merges.

### The naming convention is documented

A new coding-convention document, `docs/coding-convention/backend-database-naming.md`, in the
shape of the existing ones (title, the rules, examples, where it is checked, why):

- **Rules**: everything in "Uppercase database object names" above. Names in uppercase with
  underscores, stored that way, so quoted in every statement; explicit constraint and index names
  (`<TABLE>_PK`, `<TABLE>_<COLUMN>_FK`, `<TABLE>_<COLUMNS>_UK`, `<TABLE>_<COLUMNS>_IDX`); keywords
  and functions uppercase, unquoted; named parameters in Java camelCase; Flyway's
  `FLYWAY_SCHEMA_HISTORY`; the database, user and schema keep their lower-case names.
- **Migrations**: one Flyway migration per domain change, versions global across domains in
  order of creation, in `db/migration` of the domain's adapter; never edit an applied migration.
- **Examples**: a `CREATE TABLE` with named constraints, an index, a `SELECT` and an upsert from
  the adapters; a hand query in psql (`SELECT * FROM "ANALYSES";`).
- **Where it is checked**: the repository and Spring Boot tests on PostgreSQL catch a statement
  whose quoting does not match the schema; the stored names themselves are checked by review
  until #115 adds the automated check (linked).
- **Why**: the developer's decision on #113: one recognisable style for every database object,
  the same in migrations, adapter SQL and psql, and fixed names in the catalog rather than
  generated ones.

`docs/coding-convention/README.md` lists it in the documents table ("Backend (database): table,
column, constraint and index names, quoting, migrations") and, in the enforcement table, as
checked by the database tests and by review until #115.

### `scripts/postgres.sh` is executable

The branch already has it as mode 100755 (done by hand, finding R1-3). `mise.toml`,
`e2e/start-platform.sh` and the script's header call it as `scripts/postgres.sh <command>`, like
the other scripts, instead of `bash scripts/postgres.sh`.

## Work packages

### WP1: Datasource, migrations, persistence adapters and dependencies

- **Depends on**: none
- **Files**: `artifact/backend/src/main/resources/application.properties`,
  `artifact/backend/src/main/resources/application-postgres.properties` (deleted),
  `artifact/backend/build.gradle.kts`, `artifact/backend/src/main/java/…/TradingPlatformApplication.java`;
  every file under `artifact/backend/domain/*/adapter/src/main/resources/db/` (old migrations and
  `db/migration-postgresql/` deleted, the three new ones created); `AnalysisRow`, `UserRow`,
  `PresetRow`; the timestamp mappers listed in the design and the row mappers that use them
  (`AnalysisToAnalysisRowMapper`, `AnalysisRowToAnalysisMapper`, `UserToUserRowMapper`,
  `UserRowToUserMapper`, `PresetToPresetRowMapper`, `PresetRowToPresetMapper`, and any other that
  maps a changed column); the four `Jdbc*Adapter` classes if their parameters change;
  `gradle/libs.versions.toml`, `deploy/docker-compose.yml`, `Username.java`, `UserRepositoryPort.java`
- **Steps**:
  - [ ] Move the datasource settings into `application.properties`; delete the profile file
  - [ ] Replace the migrations with the new V1–V3
  - [ ] Rows and mappers on `OffsetDateTime` / `LocalDate`
  - [ ] Remove the `org.xerial` driver from the application's build file and the catalog
  - [ ] Drop the home directory creation and `PlatformHome` from `TradingPlatformApplication`
  - [ ] Remove `SPRING_PROFILES_ACTIVE: postgres` from the Compose file
  - [ ] Rewrite the Javadoc and comments listed in the design
- **Tests**: the mapper unit tests that exist for the replaced mappers move to the new ones (UTC
  in, UTC out, `null` stays `null`); the rest is covered by WP2 (the repository tests round-trip
  every column on PostgreSQL) and by CI's Compose smoke test (a fresh Compose database, the restart
  and `down`/`up`).

### WP2: Tests on PostgreSQL

- **Depends on**: WP1
- **Files**: `PostgresTestDatabase.java` in the four test source sets named above;
  `AdapterTestSupport.java`; `JdbcPresetRepositoryAdapterTest.java`; the identity repository tests
  (the embedded variant deleted, the contract and the PostgreSQL variant merged into
  `JdbcIdentityRepositoryTest.java`); `TestPlatformHome.java`, `TradingPlatformApplicationTests.java`,
  `AnalysesApiIntegrationTest.java`; the build files of the analysis, settings and identity
  adapters; `gradle/libs.versions.toml` (`testcontainers-junit-jupiter`, if unused)
- **Steps**:
  - [ ] Add `PostgresTestDatabase` (one container per JVM, a fresh database per call)
  - [ ] Point every repository test and both Spring Boot tests at it
  - [ ] Delete the embedded identity variant and merge the contract into one test class
  - [ ] Swap the test dependencies to the PostgreSQL driver, Flyway's PostgreSQL support and
        Testcontainers
- **Tests**: every existing repository and Spring Boot test, now on `postgres:18.6`. The identity
  contract keeps all its cases, including the case-insensitive duplicate username as
  `DuplicateKeyException`. A new analysis repository case saves and reads back an analysis with
  all three timestamps and the trade date, so the native types are covered. `mise run build`
  passes with Docker running.

### WP3: Local PostgreSQL and the end-to-end tests

- **Depends on**: WP1
- **Files**: `scripts/postgres.sh` (new), `mise.toml`, `e2e/start-platform.sh`
- **Steps**:
  - [ ] Write `scripts/postgres.sh` with `start`, `stop`, `reset` and `throwaway`, Docker or Podman
  - [ ] Add the `db`, `db:stop` and `db:reset` tasks; `run` and `dev` depend on `db`
  - [ ] Start the throwaway database in `start-platform.sh`, point the jar at it, clean up on exit
- **Tests**: `mise run e2e` passes locally and in CI's *End-to-end tests* job (the whole suite now
  runs on PostgreSQL); `mise run run` starts the container, the platform serves on :8080, and a
  second `mise run run` reuses the running container; no e2e container is left after the tests.

### WP4: CI, Renovate, docs and the last mentions

- **Depends on**: WP2, WP3
- **Files**: `.github/workflows/ci.yml` (header comment), `.github/renovate.json5`, `README.md`,
  the three version files (`mise run version:bump major`); never the older `.plans/*.md`
- **Steps**:
  - [ ] Renovate regex manager as in the design
  - [ ] CI header: the build job's tests run on PostgreSQL (Testcontainers); the e2e job's jar runs
        on a throwaway PostgreSQL container. No job change: both jobs run on `ubuntu-24.04`, which
        has Docker
  - [ ] Docs as listed below
  - [ ] `git grep -n -i -E 'sq[l]ite' -- ':!artifact/ta-runner/uv.lock' ':!.plans/79-identity-users-roles-tables.md'` prints nothing
  - [ ] `mise run version:bump major` (0.18.0 → 1.0.0)
- **Tests**: CI green; `scripts/version.sh check-bump origin/main`; the grep above.

## Tests

- Backend: every repository test (analysis, settings, identity) and both Spring Boot tests run on
  `postgres:18.6` through Testcontainers in `mise run build` (CI's *Backend and frontend* job),
  against the new V1–V3. No test on another database remains.
- End-to-end: the whole Playwright suite runs the jar on a throwaway PostgreSQL container (CI's
  *End-to-end tests* job, `mise run e2e`).
- Compose: CI's smoke test brings the stack up on a fresh data folder without the profile, runs an
  analysis and checks the data survives a database restart and `down`/`up`.
- "No mention": `git grep -n -i -E 'sq[l]ite' -- ':!artifact/ta-runner/uv.lock' ':!.plans/79-identity-users-roles-tables.md'`
  prints nothing.
- `PLAN.md` is gone and `git grep -n "PLAN\.md" -- ':!.plans'` prints nothing;
  `git diff --stat origin/main -- .plans` lists only this plan.
- Uppercase names: the repository tests and the Spring Boot tests run every rewritten statement
  on PostgreSQL, so a statement that misses a quote fails (the lower-case name does not exist).
  The automated check of the stored names (a catalog test, explicit constraint names) is issue
  #115, not this pull request.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Quick start" → "Prerequisites" | Docker or Podman is required: `mise run run` / `mise run dev` start a local PostgreSQL 18 container (`mise run db`; data in the `trading-analysis-platform-db` volume; `mise run db:stop`, `mise run db:reset`); or point `PLATFORM_DB_HOST`, `PLATFORM_DB_PORT`, `PLATFORM_DB_NAME`, `PLATFORM_DB_USER`, `PLATFORM_DB_PASSWORD` at your own PostgreSQL 18 server |
| Text | `README.md`, "Where things live" | Replace the `platform.db` row: analyses, presets and users are in PostgreSQL (the local container's volume, or your server) |
| Text | `README.md`, "Run with Docker Compose" | An "Upgrading to 1.0.0" note: the database schema was recreated, so an existing database is reset (`down`, optional `pg_dump`, delete `${PLATFORM_DATA}/postgres`, `up -d`); the platform does not start on the old one. Local runs: `mise run db:reset` is not needed for a first start; an old `~/.tradingagents-platform/platform.db` can be deleted |
| Text | `README.md`, task list / developer tasks | The new `db`, `db:stop`, `db:reset` tasks; `run`, `dev`, `e2e`, `screenshots` and `build` need Docker or Podman |
| Text | `README.md`, CI table, "Backend and frontend" and "End-to-end tests" rows | Every repository and Spring Boot test runs on PostgreSQL in a container (Testcontainers), not only the identity repository; the e2e jar runs on a throwaway PostgreSQL container |
| Text | `PLAN.md` | Deleted; every reference removed (see "`PLAN.md` is deleted") |
| Text | `.plans/*.md` (older plans) | None: never edited |
| Text | `docs/coding-convention/backend-database-naming.md` (new), `docs/coding-convention/README.md` | The naming convention and its index entries (see \"The naming convention is documented\") |
| Text | `.github/workflows/ci.yml` header comment | As in WP4 |
| Text | the other `docs/coding-convention/*.md`, `docs/event-protocol.md`, `deploy/.env.example` | None: none of them names the embedded database or the `postgres` profile today |
| Screenshot | none | No page changes; the UI is the same |

## Out of scope

- **Migrating existing data** (an old Compose database, a local `platform.db` file): the database is
  reset by the developer's decision; documented in the README and the pull request.
- **SQL rewrites** beyond the types (`ON CONFLICT … excluded.` and the `lower()` lookups are
  PostgreSQL and stay): #112 (JPA/Hibernate) replaces the JDBC adapters.
- ta-runner's `uv.lock`: generated from the upstream release's dependencies (see "No mention left
  anywhere").
