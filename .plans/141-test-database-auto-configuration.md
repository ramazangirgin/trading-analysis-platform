# Plan: The PostgreSQL test database through Spring Boot's auto-configuration

- **Issue**: #141 (Testing: provide the PostgreSQL test database through Spring Boot's Testcontainers auto-configuration instead of the static PostgresTestDatabase)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md: "any other change")

## Goal

No test builds a `DataSource` or registers `spring.datasource.*` itself any more. The adapter tests
and the application tests import one shared test configuration from the persistence test fixtures.
It contributes a `JdbcConnectionDetails` bean, and Spring Boot's own `DataSourceAutoConfiguration`
builds the Hikari pool from it, as in production. There is still one PostgreSQL container per test
JVM and a fresh database per Spring context, so test classes do not see each other's rows.
`PostgresTestDatabase` is gone.

## What changed since the issue was written

#133 (schema per domain) changed the code the issue describes:

- `TestMigrations` no longer exists. Every adapter test migrates through the domain's own Flyway bean
  (`<Domain>PersistenceConfiguration`), and each migration test has its own Flyway bean with the seed
  location (`V2AnalysisVersionMigrationTest`, `V2SettingsVersionMigrationTest`,
  `V2IdentityVersionMigrationTest`). "Migrations stay explicit per module" already holds, so this plan
  does not touch the migrations.
- The tests with their own `@Bean DataSource` are now: `AdapterTestSupport`,
  `V2AnalysisVersionMigrationTest`, `JpaIdentityRepositoryTest`, `V2IdentityVersionMigrationTest`,
  `JpaPresetRepositoryAdapterTest` and `V2SettingsVersionMigrationTest`.
- Besides `DatabaseNamingCheckTest`, two test fixtures use the database without a Spring context:
  `DomainPersistenceConventionsTest` (three of its tests) and `DomainMigrationIsolationCheck`.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend: `:backend:library:persistence` test fixtures and tests | `backend-java-persistence.md` (Tests, Shared persistence code): fixtures are the shared test code of the adapters; no `org.springframework.jdbc` in test code (Checkstyle `IllegalImport`; `org.springframework.boot.jdbc` is a different package and allowed); the catalog checks may use plain JDBC. `artifact/backend/library/persistence/README.md`: the library names no domain, not even in a Javadoc or comment. `backend-java-package-structure.md` (shared libraries). |
| backend: the domain adapters' tests (analysis, identity, settings) | `backend-java-persistence.md` (Tests): `@JpaAdapterTest`, no test-managed transaction, `@DataJpaTest` not used, migration tests with their own Flyway bean. |
| backend: `:backend` application tests | `@SpringBootTest` with `TestPlatformHome`; ArchUnit leaves the test fixtures out (`DoNotIncludeTestFixtures`). |
| Build | `gradle/libs.versions.toml` (no versions on Spring Boot modules: the BOM manages them), `repository-dependency-hygiene.md` (declare what the code uses, in the narrowest configuration). |
| CI / Renovate | `.github/renovate.json5`: the PostgreSQL regex manager keeps the Testcontainers image in step with `scripts/postgres.sh`. |
| docs | English only, the plain style of the existing documents. |

## Design

### Why not a plain `@ServiceConnection`

`@ServiceConnection` on a container cannot meet both of the issue's requirements, one container per
JVM and a fresh database per context:

- **A `@Bean @ServiceConnection PostgreSQLContainer`** is a bean of each context, so every context
  that is not cached starts a container of its own. Its destroy method also stops the container when
  the context cache evicts the context.
- **A static container (`@ImportTestcontainers`, or a `@ServiceConnection` field)** is shared, but
  every context connects to the container's one default database. A shared database breaks the
  migration tests: their Flyway history holds the seed `V1_1`, which the domain's own Flyway in
  another context does not know (validation fails), and the reverse. Cleaning per class cannot undo
  a history.

So the plan takes the issue's first option: **one container, and a `JdbcConnectionDetails` bean per
context that creates a fresh database in it**. `JdbcConnectionDetails` is the contract that
`@ServiceConnection` itself produces (Spring Boot's `JdbcContainerConnectionDetailsFactory` turns a
container into one). `DataSourceAutoConfiguration` uses it in place of the `spring.datasource.*`
properties (`PropertiesJdbcConnectionDetails` is `@ConditionalOnMissingBean`), and
`HikariJdbcConnectionDetailsBeanPostProcessor` applies it to the Hikari pool. The data source, Hikari
and the JPA wiring therefore come from auto-configuration, as in production.

Because nothing uses `@ServiceConnection` or `ContainerConnectionDetailsFactory`,
**`spring-boot-testcontainers` is not added**. The issue's first task ("add spring-boot-testcontainers")
and the acceptance line "the database comes from a `@ServiceConnection`" are met through Boot's
service-connection contract (`JdbcConnectionDetails`), not through the annotation. The pull request
says so under "Not done or done differently".

### The shared definition (test fixtures of `:backend:library:persistence`)

Package `tr.girgin.backend.trading.analysis.platform.library.persistence`, test fixtures:

- **`PostgresTestContainer`** (new, package-private, final) replaces `PostgresTestDatabase` and is
  the one place that holds the image tag (`postgres:18.6`, unchanged):
  - the static `PostgreSQLContainer`, started on first use (holder-class idiom, as today), stopped
    with the JVM by Testcontainers (Ryuk). No reuse flag;
  - `static JdbcConnectionDetails newDatabase()`: `CREATE DATABASE test_<n>` on the container (a
    counter, as today), and the details of that database: JDBC URL (host, mapped port, name),
    username, password. `getDriverClassName()` keeps its default (derived from the URL);
  - `static DataSource dataSource(JdbcConnectionDetails)`: a `PGSimpleDataSource` (one connection
    per use, no pool to close) for the catalog checks that run without a Spring context.
  Package-private: its only callers outside Spring are in this package (`DomainPersistenceConventionsTest`,
  `DomainMigrationIsolationCheck`, and `DatabaseNamingCheckTest` in the module's tests). Every other test
  goes through the configuration below.
- **`TestDatabaseConfiguration`** (new, public): `@Bean JdbcConnectionDetails testDatabaseConnectionDetails()`
  returning `PostgresTestContainer.newDatabase()`. It is not annotated `@Configuration`, for the same
  reason as `TestClockConfiguration`: the application's component scan covers this package and the
  application tests have the fixtures on their classpath. It is only imported. One bean per context
  gives a fresh database per context, and a cached context keeps its database, as today.
- **`@JpaAdapterTest`**: adds `DataSourceAutoConfiguration` to `@ImportAutoConfiguration` and
  `TestDatabaseConfiguration` to `@Import`. Its Javadoc says the database comes from
  `TestDatabaseConfiguration` through `DataSourceAutoConfiguration`, and the test's `Config` provides
  no data source any more.
- **`DomainPersistenceConventionsTest`** and **`DomainMigrationIsolationCheck`**:
  `PostgresTestDatabase.create().dataSource()` becomes
  `PostgresTestContainer.dataSource(PostgresTestContainer.newDatabase())`. Behaviour is unchanged.
- **`PostgresTestDatabase`** is deleted.

### Connections

Each cached context now holds a Hikari pool. The pool size is the default (10), as in the
application, and `jpa-test.properties` sets no pool settings: `application.properties` sets
`maximum-pool-size=10`, which equals Hikari's default. The heaviest JVMs (the analysis adapter
tests, the `:backend` tests) hold three or four contexts, so they stay well under PostgreSQL's 100
connections. If the build shows otherwise, the developer stops and reports rather than tuning pools
(out of scope).

### Build

- `gradle/libs.versions.toml`: `spring-boot-jdbc = { module = "org.springframework.boot:spring-boot-jdbc" }`
  (holds `JdbcConnectionDetails` and `DataSourceAutoConfiguration`, version from the BOM).
- `artifact/backend/library/persistence/build.gradle.kts`: `testFixturesApi(libs.spring.boot.jdbc)`.
  It is `api` because `@JpaAdapterTest` names `DataSourceAutoConfiguration` and the consumers read the
  annotation, like the other auto-configuration modules there. The comment on
  `testFixturesRuntimeOnly(libs.spring.boot.starter.data.jpa)` stays true: it brings Hikari.
  `testImplementation(libs.spring.boot.test)` for `ApplicationContextRunner` in the new test (WP1).
- `artifact/backend/build.gradle.kts`: nothing new. It already has `testFixtures(...)`, and the
  application's runtime has the JDBC starter.

### The adapter tests

The `@Bean DataSource dataSource()` method, and the `javax.sql.DataSource` and `PostgresTestDatabase`
imports where they become unused, are removed from the `Config` of:

- `AdapterTestSupport` (analysis), `V2AnalysisVersionMigrationTest`;
- `JpaIdentityRepositoryTest`, `V2IdentityVersionMigrationTest`;
- `JpaPresetRepositoryAdapterTest`, `V2SettingsVersionMigrationTest`.

The migration tests' own `Flyway` beans keep their `DataSource` parameter. It is now the
auto-configured Hikari data source. The Javadoc and comments of these classes stay true. A
`Config` with no bean method left stays as it is: it still carries `@AutoConfigurationPackage` and
the `@ComponentScan`.

### The application tests

- `TestPlatformHome.register(...)` no longer creates a database or registers `spring.datasource.*`.
  Its Javadoc drops "and a fresh PostgreSQL database for it".
- `TradingPlatformApplicationTests`, `AnalysesApiIntegrationTest` and `PresetsApiIntegrationTest`
  each get `@Import(TestDatabaseConfiguration.class)`. Each class has its own `@DynamicPropertySource`
  (its own home), so it is a context of its own with a database of its own, as today. The real
  `application.properties` still sets `spring.datasource.url`. The `JdbcConnectionDetails` bean takes
  precedence over it, and the Hikari settings (`maximum-pool-size`) still apply.

### `DatabaseNamingCheckTest`

`PostgresTestDatabase.create().dataSource()` becomes
`PostgresTestContainer.dataSource(PostgresTestContainer.newDatabase())`, in both tests. It needs no
Spring context, so it uses the same container definition without one.

## Work packages

### WP1: Shared test database in the persistence test fixtures

- **Depends on**: none
- **Status**: done
- **Files**:
  - `gradle/libs.versions.toml`
  - `artifact/backend/library/persistence/build.gradle.kts`
  - `artifact/backend/library/persistence/src/testFixtures/java/tr/girgin/backend/trading/analysis/platform/library/persistence/PostgresTestContainer.java` (new)
  - `…/library/persistence/TestDatabaseConfiguration.java` (new, test fixtures)
  - `…/library/persistence/JpaAdapterTest.java` (test fixtures)
  - `…/library/persistence/DomainPersistenceConventionsTest.java` (test fixtures)
  - `…/library/persistence/DomainMigrationIsolationCheck.java` (test fixtures)
  - `…/library/persistence/PostgresTestDatabase.java` (deleted)
  - `artifact/backend/library/persistence/src/test/java/…/library/persistence/DatabaseNamingCheckTest.java`
  - `artifact/backend/library/persistence/src/test/java/…/library/persistence/TestDatabaseConfigurationTest.java` (new)
- **Steps**:
  - [x] Catalog entry `spring-boot-jdbc`; `testFixturesApi(libs.spring.boot.jdbc)` and
        `testImplementation(libs.spring.boot.test)` in the library's build. Done differently: no direct
        `spring-boot-test` declaration, because the `java-library` convention plugin already adds
        `spring-boot-starter-test` as the one permitted test aggregator, which brings `ApplicationContextRunner`.
  - [x] `PostgresTestContainer` and `TestDatabaseConfiguration` as in the design, with Javadoc that
        says why it is one container with a database per context, and not `@ServiceConnection`.
  - [x] `@JpaAdapterTest`: `DataSourceAutoConfiguration`, `@Import` of `TestDatabaseConfiguration`,
        Javadoc.
  - [x] Move `DomainPersistenceConventionsTest`, `DomainMigrationIsolationCheck` and
        `DatabaseNamingCheckTest` to `PostgresTestContainer`; delete `PostgresTestDatabase`.
- **Tests**: `TestDatabaseConfigurationTest` (new, in the library's tests, made-up names only).
  With `ApplicationContextRunner`, `DataSourceAutoConfiguration` and `TestDatabaseConfiguration`:
  - each context's `DataSource` is a `HikariDataSource`;
  - two contexts get JDBC URLs on the same host and port (one container) but different databases;
  - a table created through one context's data source does not exist in the other's.

  It proves "one container per JVM, test classes do not see each other's rows". The existing
  `DatabaseNamingCheckTest`, `DomainMigrationIsolationCheckTest` and `SamplePersistenceConventionsTest`
  keep passing unchanged.

### WP2: Adapter and application tests off their own data source

- **Depends on**: WP1
- **Status**: done
- **Files**:
  - `artifact/backend/domain/analysis/adapter/src/test/java/…/domain/analysis/adapter/AdapterTestSupport.java`
  - `…/domain/analysis/adapter/persistence/V2AnalysisVersionMigrationTest.java`
  - `artifact/backend/domain/identity/adapter/src/test/java/…/domain/identity/adapter/persistence/JpaIdentityRepositoryTest.java`
  - `…/domain/identity/adapter/persistence/V2IdentityVersionMigrationTest.java`
  - `artifact/backend/domain/settings/adapter/src/test/java/…/domain/settings/adapter/persistence/JpaPresetRepositoryAdapterTest.java`
  - `…/domain/settings/adapter/persistence/V2SettingsVersionMigrationTest.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/TestPlatformHome.java`
  - `…/platform/TradingPlatformApplicationTests.java`, `…/platform/AnalysesApiIntegrationTest.java`,
    `…/platform/PresetsApiIntegrationTest.java`
  - `…/platform/DoNotIncludeTestFixtures.java` (Javadoc only)
- **Steps**:
  - [x] Remove the `@Bean DataSource` and the now-unused imports from the six adapter tests.
  - [x] `TestPlatformHome`: drop the database and the `spring.datasource.*` registration, and fix the
        Javadoc.
  - [x] `@Import(TestDatabaseConfiguration.class)` on the three `@SpringBootTest` classes.
  - [x] `DoNotIncludeTestFixtures` Javadoc: name `@JpaAdapterTest` and `TestDatabaseConfiguration`
        instead of `PostgresTestDatabase`.
  - [x] `git grep PostgresTestDatabase` finds nothing outside `.plans/`.
- **Tests**: no assertion changes. Every adapter test and application test passes on the
  auto-configured data source, which proves the wiring. `ddl-auto=validate` still validates every
  module's entities against its migrations. The migration tests still read their seeded rows, which
  proves each context has a database of its own (a shared one would fail Flyway's validation).

### WP3: Renovate and documentation

- **Depends on**: WP1 (the file name)
- **Status**: done
- **Files**:
  - `.github/renovate.json5`
  - `docs/coding-convention/backend-java-persistence.md`
  - `artifact/backend/library/persistence/README.md`
  - `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py` (version bump)
- **Steps**:
  - [x] `renovate.json5`: `managerFilePatterns` matches `/PostgresTestContainer\\.java$/` instead of
        `PostgresTestDatabase`, and the comment above it names the new class. The `matchStrings`
        regex (`postgres:<tag>`) is unchanged, so the literal stays `"postgres:18.6"` in that file.
        Check with `npx --yes --package renovate -- renovate-config-validator .github/renovate.json5`
        if the repository's checks do not already validate it.
  - [x] Docs as in "Docs to update".
  - [x] `mise run version:bump minor` (3.1.0 → 3.2.0, or the next minor above `main` at the time).
- **Tests**: `mise run check` (formatting, Checkstyle, the version check). The Renovate config is
  validated as above.

### WP4: A container per context through `@ServiceConnection` (the developer's review)

- **Depends on**: WP1, WP2, WP3
- **Status**: open
- **Why**: the developer reviewed the pull request and asked to drop the custom way
  (`JdbcConnectionDetails` from a database created on a shared container). The Spring tests should use
  Spring Boot's own Testcontainers support, as in
  https://www.baeldung.com/spring-boot-testcontainers-integration-test. This package **replaces the
  decision "one container per JVM, a fresh database per context"** in "Why not a plain
  `@ServiceConnection`" and the "Design" above: **one container per Spring context**. Isolation
  between test classes comes for free, because each context has a container of its own and the
  context's destroy stops it. The price is one container start per context: about 9 contexts in the
  whole build, spread over 5 test JVMs, a few seconds each. The Testcontainers reuse flag stays off.
  Where this package and the earlier sections disagree, this package wins, and its last step brings
  the earlier sections in line.
- **Files**:
  - `gradle/libs.versions.toml`, `artifact/backend/library/persistence/build.gradle.kts`
  - test fixtures of `:backend:library:persistence`: `TestDatabaseConfiguration.java`,
    `JpaAdapterTest.java`, `PostgresTestContainer.java`
  - `artifact/backend/library/persistence/src/test/java/…/library/persistence/TestDatabaseConfigurationTest.java`
  - the three `@SpringBootTest` classes in `artifact/backend/src/test/java/…/platform/`, only if they
    need a change to get the container started (see the steps)
  - `docs/coding-convention/backend-java-persistence.md`, `artifact/backend/library/persistence/README.md`,
    `.github/renovate.json5` (only if the image tag moves to another file)
  - `.plans/141-test-database-auto-configuration.md` (this plan: "Goal", "Design", "Tests", "Docs to
    update" and "Out of scope" match the new decision)
- **Steps**:
  - [ ] Catalog entry `spring-boot-testcontainers = { module = "org.springframework.boot:spring-boot-testcontainers" }`
        (version from the BOM), as `testFixturesApi` of the library. Keep or drop `spring-boot-jdbc`
        as `analyzeDependencies` says: `@JpaAdapterTest` still names `DataSourceAutoConfiguration`.
  - [ ] Confirm against Spring Boot 4.1.1 first: the package of `@ServiceConnection`, the
        auto-configuration that turns a `@ServiceConnection` bean into `JdbcConnectionDetails`
        (`ServiceConnectionAutoConfiguration`), and what starts a container bean
        (`TestcontainersLifecycleApplicationContextInitializer` and its bean post-processor).
  - [ ] `TestDatabaseConfiguration` (still public, still not `@Configuration`, still only imported) holds
        `@Bean @ServiceConnection PostgreSQLContainer postgres()` returning a new container of the one image
        (`postgres:18.6`, the constant stays where Renovate's manager looks). It no longer creates a
        database or returns `JdbcConnectionDetails` itself: the container's default database is the
        context's own.
  - [ ] `@JpaAdapterTest`: add the service-connection auto-configuration to `@ImportAutoConfiguration`,
        next to `DataSourceAutoConfiguration`. Make sure the container is started before the data source
        connects, in the way Spring Boot provides for a plain `@SpringJUnitConfig` context, which does
        not apply `spring.factories` initializers the way `SpringApplication` does. Prefer Boot's own
        mechanism (the lifecycle initializer or bean post-processor, or `@ImportTestcontainers` if it
        is the documented way) over calling `start()` by hand. Say in the Javadoc which one and why.
        The `@SpringBootTest` classes keep `@Import(TestDatabaseConfiguration.class)`. Their
        `SpringApplication` already applies Boot's initializers, so check that they need nothing more.
  - [ ] `PostgresTestContainer` stays only for the catalog checks without a Spring context
        (`DomainPersistenceConventionsTest`, `DomainMigrationIsolationCheck`, `DatabaseNamingCheckTest`):
        its one lazily started container and a fresh database per check, as now. Simplify its API to
        what those callers need (a `DataSource` of a fresh database), with no `JdbcConnectionDetails`
        unless still useful. It and `TestDatabaseConfiguration` share one image constant, so Renovate
        updates both. Its Javadoc says why it still exists: the checks run outside Spring and need many
        empty databases.
  - [ ] `TestDatabaseConfigurationTest`: two contexts get different containers (different mapped
        ports), the data source is Hikari, a table created in one is absent in the other, and a closed
        context stops its container.
  - [ ] Docs: `backend-java-persistence.md` (Tests) describes one container per context through
        `@ServiceConnection`, why (isolation without custom code, the developer's choice in review),
        its cost (a container start per context), and why the catalog checks keep `PostgresTestContainer`.
        Remove the reasoning against a plain `@ServiceConnection`. The library README follows. Change
        the Renovate pattern only if the image constant moves to another file.
  - [ ] Bring this plan's earlier sections in line with this package.
  - [ ] No version bump: the branch is already at 3.3.0, above `main`. Bump only if
        `scripts/version.sh check-bump origin/main` fails.
- **Tests**: `TestDatabaseConfigurationTest` as above. Every adapter test, the migration tests with
  their seeded histories, and the three `@SpringBootTest` classes pass on a container per context. The
  library's catalog checks pass unchanged. `mise run check` and the backend tests are green.

## Tests

- Unit and integration: the whole backend suite on Testcontainers (`mise run build`): the library's
  checks and the new `TestDatabaseConfigurationTest`, every adapter test (`@JpaAdapterTest`), every
  domain's `<Domain>PersistenceConventionsTest`, and the three `@SpringBootTest` classes.
- Coverage must not drop. The deleted `PostgresTestDatabase` code moves into `PostgresTestContainer`,
  and every method of the new classes is exercised by the tests above.
- The issue's acceptance:
  - no `@Bean DataSource` and no `spring.datasource.*` in any test (`git grep -n "DataSource dataSource()\|spring.datasource" -- '*Test*.java' 'artifact/backend/src/test'` finds nothing);
  - one container per JVM and isolated contexts (`TestDatabaseConfigurationTest`, the migration
    tests);
  - `PostgresTestDatabase` gone, and Renovate's manager on the new file;
  - `mise run build` green; the convention updated.
- No frontend, ta-runner or e2e change.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-persistence.md`, "Tests" | The `@JpaAdapterTest` bullet: the test's `Config` no longer provides the data source. `TestDatabaseConfiguration` (imported by `@JpaAdapterTest` and by the application tests) contributes a `JdbcConnectionDetails` bean for a fresh database in the one container per JVM (`PostgresTestContainer`), and `DataSourceAutoConfiguration` builds the Hikari pool from it, as in production. Why: a database per context keeps test classes apart, and the migration tests' seeded Flyway histories need it; why not a plain `@ServiceConnection` (a container per context, or one shared database); why not the reuse flag. The catalog checks without a Spring context take a `PGSimpleDataSource` from the same container. |
| Text | `docs/coding-convention/backend-java-persistence.md`, "Rules" and "Shared persistence code" | "the test fixtures may create the database and a `DataSource` to run the migrations": still true, so reword only if it names `PostgresTestDatabase` (it does not). "Shared persistence code": list `TestDatabaseConfiguration` among the fixtures. |
| Text | `artifact/backend/library/persistence/README.md`, "What it holds" | `PostgresTestDatabase` → `TestDatabaseConfiguration` (used by the adapter tests through `@JpaAdapterTest` and by `:backend`'s tests); `PostgresTestContainer` named as the one container, used by the catalog checks. |
| Text | `@JpaAdapterTest`, `DoNotIncludeTestFixtures`, `TestPlatformHome` Javadoc | As in the design. |
| Text | `.github/renovate.json5` comment | Names `PostgresTestContainer`. |
| Text | Root `README.md` | None: it does not describe the test database wiring, the CI jobs do not change, and `mise.toml` does not change. |
| Screenshot | none | No page changes. |

## Out of scope

- `spring-boot-testcontainers`, `@ServiceConnection` and `@ImportTestcontainers`: not needed for this
  design (see "Why not a plain `@ServiceConnection`"). Testcontainers at development time
  (`TestApplication`, `spring-boot:test-run`) stays out of scope as the issue says, and would be
  the place to add them.
- Changing what the tests assert, the no-test-managed-transaction rule, `@DataJpaTest`.
- The migrations per test: they already come from each domain's Flyway bean (since #133).
- Hikari pool tuning for tests, the PostgreSQL version, the Testcontainers reuse flag.
- The old plan files under `.plans/` that mention `PostgresTestDatabase`: they are history and are not
  edited.
