# Plan: PostgreSQL only, remove SQLite

- **Issue**: #113 (Persistence: PostgreSQL only, remove SQLite)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md: native runs
  lose their SQLite database with no migration, and the `postgres` profile, a configuration
  setting, goes away)

## Goal

PostgreSQL 18 is the platform's only database. `mise run run` and `mise run dev` start a local
`postgres:18.6` container (Docker or Podman) on their own, or use the developer's own server through
the `PLATFORM_DB_*` variables. The end-to-end tests run the jar against a throwaway PostgreSQL
container, and every repository and Spring Boot test runs on PostgreSQL through Testcontainers, so
the database the tests use is the one the platform runs on. `sqlite-jdbc`, the `postgres` profile,
the second migration location and the SQLite test variants are gone. An existing Docker Compose
database upgrades without a manual step. Data in a local `~/.tradingagents-platform/platform.db` is
not migrated; the README and the pull request (release notes) say so.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend | `docs/coding-convention/backend-java-package-structure.md`: test helpers stay in the test sources of the module that uses them, in the package of the code under test (`….domain.<d>.adapter.persistence`, `….domain.analysis.adapter`, `…` for `:backend`); no new Gradle module. `backend-java-checkstyle.md` and `backend-java-formatting.md` for every changed Java file (`mise run check`). Flyway: versions are global across domains, applied migrations are never edited (their checksum would change). Libraries come from the version catalog `gradle/libs.versions.toml`, versions from the Spring Boot BOM. |
| e2e | `e2e/start-platform.sh` stays the one entry point Playwright's `webServer` (and `mise run screenshots`) starts. |
| scripts, mise | Developer entry points are `mise` tasks in `mise.toml` with a `description`; shell scripts under `scripts/` with a header comment saying what they do and what they need, as `scripts/version.sh` does. Docker and Podman must both work, as in `deploy/`. |
| deploy | `deploy/docker-compose.yml` keeps its `PLATFORM_DB_*` variables; only the profile goes. |
| CI, Renovate | `.github/workflows/ci.yml` header comment lists what each job runs. `.github/renovate.json5`: the Testcontainers PostgreSQL image follows the Compose image (same group, no majors). |
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
  `runtimeOnly(libs.flyway.database.postgresql)` without the "postgres profile" comment; no
  `sqlite-jdbc`.
- `TradingPlatformApplication.main` no longer creates the platform home ("SQLite does not create
  the database's directory itself"): the adapters that write there create their directories
  themselves (`JsonlEventStoreAdapter`, the runner adapters, `DotenvSecretStoreAdapter`). The
  `PlatformHome` helper goes with it, and any test of it.

### Migrations

- `db/migration-postgresql/V4_1__analysis_postgresql_column_types.sql` moves unchanged (byte for
  byte) to the analysis adapter's `db/migration/`. Flyway records the script name, version and
  checksum, not the location, so a Compose database that already applied V4_1 validates as before,
  and a new database gets V1 → V5 with V4_1 in order. The `db/migration-postgresql` directory is
  removed.
- No other migration is edited. Their comments that mention SQLite (V4_1, V5) stay as they are:
  editing them would change their checksums and fail Flyway's validation on every existing
  database. This is the one exception to "no SQLite mention in the backend".
- No new type-cleanup migration in this plan (see "Out of scope"): converting the ISO-text
  timestamps to `timestamptz` changes the row records and mappers of three domains, which #112
  (JPA/Hibernate) rewrites anyway.

### Local PostgreSQL for `mise run run` and `mise run dev` (option b of the issue)

A new script `scripts/postgres.sh` (bash, `set -euo pipefail`), with the engine taken from
`CONTAINER_ENGINE`, else `docker` when it is on `PATH`, else `podman`:

- `scripts/postgres.sh start`: when `PLATFORM_DB_HOST` is set to anything other than `localhost` or
  `127.0.0.1`, prints that the developer's own server is used and exits 0 (option c). Otherwise
  starts the container `trading-analysis-platform-db` if it is not running (creates it the first
  time, `start`s it when it exists but is stopped): image `postgres:18.6`, named volume
  `trading-analysis-platform-db` on `/var/lib/postgresql`, port `127.0.0.1:${PLATFORM_DB_PORT:-5432}:5432`,
  `POSTGRES_DB=platform`, `POSTGRES_USER=platform`, `POSTGRES_HOST_AUTH_METHOD=trust` (the port is
  published on 127.0.0.1 only, so the application's default empty password works). Then waits up to
  60 s for `pg_isready -U platform -d platform` inside the container, and fails with the
  container's last log lines otherwise.
- `scripts/postgres.sh stop`: stops the container; the volume keeps the data.
- `scripts/postgres.sh throwaway`: for the end-to-end tests. Starts a `--rm` container labelled
  `trading-analysis-platform.e2e=true` with a random host port (`-p 127.0.0.1::5432`), waits for
  `pg_isready`, and prints `<container id> <host port>` on stdout. Before starting, it removes
  stopped or leftover containers with that label (a test run killed with SIGKILL cannot clean up).

`mise.toml`:

- new task `db` ("Start the local PostgreSQL for run and dev (Docker or Podman), unless
  PLATFORM_DB_HOST points elsewhere"): `scripts/postgres.sh start`;
- new task `db:stop`: `scripts/postgres.sh stop`;
- `run` and `dev` get `depends = ["db"]`.

### End-to-end tests

`e2e/start-platform.sh` starts `scripts/postgres.sh throwaway`, exports `PLATFORM_DB_HOST=127.0.0.1`
and `PLATFORM_DB_PORT=<host port>`, and runs the jar in the background instead of `exec`: a trap on
`EXIT INT TERM` stops the jar and removes the container, and the script `wait`s for the jar so
Playwright's `webServer` still sees one long-running process. The throwaway platform home stays
(run directories, secrets file). `mise run e2e` and `mise run screenshots` need no change beyond
their descriptions mentioning that Docker or Podman is required.

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
  new test-support module, which the package-structure convention does not have; four copies of a
  30-line class are cheaper than a new module kind.
- Docker (or Podman) is required to run the backend tests: no `disabledWithoutDocker`, so a
  missing engine fails the build loudly instead of skipping every database test. The README's
  prerequisites say so; CI's runners have Docker.
- Analysis adapter: `AdapterTestSupport.Config.dataSource()` migrates a fresh PostgreSQL database
  (all migrations, as today) instead of `jdbc:sqlite:…/test.db`. `build.gradle.kts`:
  `testRuntimeOnly(libs.postgresql)`, `testRuntimeOnly(libs.flyway.database.postgresql)`,
  `testImplementation(libs.testcontainers.postgresql)` instead of `sqlite-jdbc`.
- Settings adapter: `JdbcPresetRepositoryAdapterTest.Config.dataSource()` the same way, keeping
  its `baselineVersion("1")` (only this domain's migration on the module's classpath). Same build
  changes.
- Identity adapter: `SqliteIdentityRepositoryTest` is deleted. `IdentityRepositoryContractTest`
  and `PostgresqlIdentityRepositoryTest` are merged into one concrete `JdbcIdentityRepositoryTest`
  (the contract's tests, `DuplicateKeyException` asserted directly, no
  `duplicateUsernameException()` hook), using `PostgresTestDatabase` instead of its own
  `@Container`. `testcontainers-junit-jupiter` is no longer needed there and is removed from the
  module and, when nothing else uses it, from the version catalog.
- `:backend`: `TestPlatformHome.register` also registers `spring.datasource.url`, `username` and
  `password` of a fresh `PostgresTestDatabase` per test class, so `TradingPlatformApplicationTests`
  and `AnalysesApiIntegrationTest` run on PostgreSQL (`AnalysesApiIntegrationTest`'s Javadoc says
  PostgreSQL instead of SQLite). `build.gradle.kts`: `testImplementation(libs.testcontainers.postgresql)`.
- `sqlite-jdbc` is removed from `gradle/libs.versions.toml` and every build file.

### Javadoc that names SQLite

- `Username`: lookups ignore case with PostgreSQL's `lower()`, whose result for non-ASCII letters
  depends on the database's locale; ASCII only keeps "which names are equal" independent of it.
  The rule itself does not change.
- `UserRepositoryPort.save`: a username taken by another user fails with Spring's
  `DuplicateKeyException`.

### Renovate

The custom regex manager for the Testcontainers image matches every `PostgresTestDatabase.java`
and `scripts/postgres.sh` (`postgres:(?<currentValue>[0-9][^"\s]*)`), instead of
`PostgresqlIdentityRepositoryTest.java`. It stays under the existing "PostgreSQL: no major updates"
rule, so the Compose, script and test images move together.

## Work packages

### WP1: Datasource, migrations and dependencies

- **Depends on**: none
- **Files**: `artifact/backend/src/main/resources/application.properties`,
  `artifact/backend/src/main/resources/application-postgres.properties` (deleted),
  `artifact/backend/build.gradle.kts`,
  `artifact/backend/src/main/java/…/TradingPlatformApplication.java`,
  `artifact/backend/domain/analysis/adapter/src/main/resources/db/migration-postgresql/V4_1__analysis_postgresql_column_types.sql`
  (moved to `…/db/migration/`), `gradle/libs.versions.toml`, `deploy/docker-compose.yml`,
  `Username.java`, `UserRepositoryPort.java`
- **Steps**:
  - [ ] Move the datasource settings into `application.properties`; delete the profile file
  - [ ] `git mv` V4_1 into `db/migration/` without changing a byte
  - [ ] Remove `sqlite-jdbc` from the application's build file and the catalog
  - [ ] Drop the home directory creation and `PlatformHome` from `TradingPlatformApplication`
  - [ ] Remove `SPRING_PROFILES_ACTIVE: postgres` from the Compose file
  - [ ] Update the two Javadoc comments
- **Tests**: covered by WP2 (the Spring Boot tests start the application on PostgreSQL with the
  single migration location) and by CI's Compose smoke test (a fresh Compose database, the restart
  and `down`/`up`).

### WP2: Tests on PostgreSQL

- **Depends on**: WP1 (the catalog and the migration location)
- **Files**: `PostgresTestDatabase.java` in the four test source sets named above;
  `AdapterTestSupport.java`; `JdbcPresetRepositoryAdapterTest.java`;
  `SqliteIdentityRepositoryTest.java` (deleted), `IdentityRepositoryContractTest.java` and
  `PostgresqlIdentityRepositoryTest.java` (merged into `JdbcIdentityRepositoryTest.java`);
  `TestPlatformHome.java`, `TradingPlatformApplicationTests.java`, `AnalysesApiIntegrationTest.java`;
  the build files of the analysis, settings and identity adapters; `gradle/libs.versions.toml`
  (`testcontainers-junit-jupiter`, if unused)
- **Steps**:
  - [ ] Add `PostgresTestDatabase` (one container per JVM, a fresh database per call)
  - [ ] Point every repository test and both Spring Boot tests at it
  - [ ] Delete the SQLite identity variant and merge the contract into one test class
  - [ ] Swap the test dependencies from `sqlite-jdbc` to the PostgreSQL driver, Flyway's PostgreSQL
        support and Testcontainers
- **Tests**: every existing repository and Spring Boot test, now on `postgres:18.6`. The identity
  contract keeps all its cases, including the case-insensitive duplicate username as
  `DuplicateKeyException`. `mise run build` passes with Docker running; `grep -ri sqlite` over
  `artifact/backend` finds only the two applied migrations' comments.

### WP3: Local PostgreSQL and the end-to-end tests

- **Depends on**: WP1
- **Files**: `scripts/postgres.sh` (new), `mise.toml`, `e2e/start-platform.sh`
- **Steps**:
  - [ ] Write `scripts/postgres.sh` with `start`, `stop` and `throwaway`, Docker or Podman
  - [ ] Add the `db` and `db:stop` tasks; `run` and `dev` depend on `db`
  - [ ] Start the throwaway database in `start-platform.sh`, point the jar at it, clean up on exit
- **Tests**: `mise run e2e` passes locally and in CI's *End-to-end tests* job (the whole suite now
  runs on PostgreSQL); `mise run run` starts the container, the platform serves on :8080, and a
  second `mise run run` reuses the running container; `docker ps -a` shows no e2e container after
  the tests.

### WP4: CI, Renovate and docs

- **Depends on**: WP2, WP3
- **Files**: `.github/workflows/ci.yml` (header comment), `.github/renovate.json5`, `README.md`,
  `PLAN.md`, `docs/coding-convention/README.md` only if it names the database (it does not today),
  the three version files (`mise run version:bump major`)
- **Steps**:
  - [ ] Renovate regex manager as in the design
  - [ ] CI header: the build job's tests run on PostgreSQL (Testcontainers); the e2e job's jar runs
        on a throwaway PostgreSQL container. No job change is needed: both jobs run on
        `ubuntu-24.04`, which has Docker
  - [ ] Docs as listed below
  - [ ] `mise run version:bump major` (0.18.0 → 1.0.0)
- **Tests**: CI green; `scripts/version.sh check-bump origin/main`.

## Tests

- Backend: every repository test (analysis, settings, identity) and both Spring Boot tests run on
  `postgres:18.6` through Testcontainers in `mise run build` (CI's *Backend and frontend* job). No
  SQLite test remains.
- End-to-end: the whole Playwright suite runs the jar on a throwaway PostgreSQL container (CI's
  *End-to-end tests* job, `mise run e2e`).
- Compose: CI's smoke test brings the stack up without the profile, runs an analysis and checks the
  data survives a database restart and `down`/`up`.
- Upgrade of an existing Compose database: V4_1 keeps its name and bytes, so its checksum and
  version are unchanged; Flyway validates the history of a database migrated before this change.
- The issue's "done when": `git grep -i sqlite -- artifact/backend gradle e2e deploy docs scripts README.md mise.toml .github`
  finds only the comments of the applied migrations V4_1 and V5.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Quick start" → "Prerequisites" | Docker or Podman is required: `mise run run` / `mise run dev` start a local PostgreSQL 18 container (`mise run db`, data in the `trading-analysis-platform-db` volume, `mise run db:stop`); or point `PLATFORM_DB_HOST`, `PLATFORM_DB_PORT`, `PLATFORM_DB_NAME`, `PLATFORM_DB_USER`, `PLATFORM_DB_PASSWORD` at your own PostgreSQL 18 server |
| Text | `README.md`, "Where things live" | Replace the `platform.db (SQLite)` row: analyses, presets and users are in PostgreSQL (the local container's volume, or your server). Add a note: data in an old `~/.tradingagents-platform/platform.db` from before 1.0.0 is not migrated and can be deleted |
| Text | `README.md`, task list / developer tasks | The new `db` and `db:stop` tasks; `run`, `dev`, `e2e` and `screenshots` need Docker or Podman |
| Text | `README.md`, CI table, "Backend and frontend" and "End-to-end tests" rows | Every repository and Spring Boot test runs on PostgreSQL in a container (Testcontainers), not only the identity repository; the e2e jar runs on a throwaway PostgreSQL container |
| Text | `PLAN.md` | D13 (line 20), the persistence row (158), the Phase notes (435, 456, 460) and the adapter testing line (598): PostgreSQL is the only database, tests on Testcontainers done. Line 47 (upstream's LangGraph checkpoint SQLite in ta-runner) is not the platform's database and stays |
| Text | `.github/workflows/ci.yml` header comment | As in WP4 |
| Text | `docs/coding-convention/*.md`, `docs/event-protocol.md`, `deploy/.env.example` | None: none of them names SQLite or the `postgres` profile today (checked with `git grep -i sqlite`) |
| Screenshot | none | No page changes; the UI is the same |

## Out of scope

- **`timestamptz` and other type cleanups** (ISO-8601 text timestamps in `analyses`, `presets`,
  `users`; `trade_date` as text): left to #112, which replaces the JDBC rows and MapStruct mappers
  with JPA entities anyway, so the timestamp handling is changed once, there. The issue allows this
  ("Otherwise leave that to #112").
- **SQL simplifications** that only existed for two dialects (`ON CONFLICT … excluded.` is valid
  PostgreSQL and stays; `lower()` lookups stay with the unique index on `lower(username)`): #112.
- **Migrating local SQLite data** (`~/.tradingagents-platform/platform.db`): not done, documented
  in the README and the pull request.
- **Historic plans in `.plans/`** that mention SQLite: records of earlier work, not documentation
  of the current state; unchanged.
- ta-runner's `uv.lock` (`langgraph-checkpoint-sqlite`, upstream's checkpointing): not the
  platform's database.
