# Backend: Java persistence

How the backend stores data: Spring Data JPA with Hibernate on PostgreSQL, the schema owned by
Flyway. Where each persistence class lives, how domain types map to columns, and which check
enforces each rule. Table, column and type names follow
[backend-database-naming.md](backend-database-naming.md). Packages follow
[backend-java-package-structure.md](backend-java-package-structure.md).

## Rules

- **Only Spring Data JPA / Hibernate.** Production code uses no `JdbcClient`, `JdbcTemplate`,
  `NamedParameterJdbcTemplate`, raw `DataSource` or `Connection` (`java.sql`, `javax.sql`). The one
  exception is `javax.sql.DataSource` in a `<Domain>PersistenceConfiguration`, which only hands it to
  the domain's Flyway bean. Test code
  uses no `org.springframework.jdbc`; the test fixtures may create the database and a `DataSource`
  to run the migrations. Neither uses a native query (`@Query(nativeQuery = true)`, `@NativeQuery`,
  `EntityManager.createNativeQuery`, Hibernate's `createNativeMutationQuery`, stored procedure
  queries). Queries are derived methods, JPQL `@Query`, or `Specification`s. The reasons:
  - one mapping per table instead of SQL strings that drift from the migrations;
  - every query is typed against the entities, so a rename fails at startup instead of at run time;
  - Hibernate checks every entity against the schema at startup (`ddl-auto=validate`).
- **No `spring-jdbc` declared.** `spring-jdbc` is not in the version catalog and no module declares
  it. It still arrives at runtime: Spring Data JPA needs `spring-orm`, whose `JpaTransactionManager`
  and exception translation are built on it. So the compiler cannot stop an import; ArchUnit
  (main code) and Checkstyle (test code) do.
- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate`: Hibernate never creates or
  alters a table, and an entity that does not match its migration fails at startup. Every schema
  change is a new migration ([backend-database-naming.md](backend-database-naming.md#migrations)).
- **The domain core knows nothing of persistence.** A core never imports `jakarta.persistence`,
  `org.hibernate` or `org.springframework.data`. Entities, embeddables, converters and repositories
  live in the adapter, and MapStruct maps entity ↔ domain model at the adapter boundary. The
  `*RepositoryPort` interfaces stay persistence-agnostic.

## One schema per domain

Each domain keeps its tables, enum types, constraints and indexes in a PostgreSQL schema of its own
(`"ANALYSIS"`, `"SETTINGS"`, `"IDENTITY"`), the database side of the package boundary
([package structure](backend-java-package-structure.md)). Nothing of the application is in `public`.

- **No object crosses a schema**: no foreign key, view, type or function refers to another domain's
  schema. A domain refers to another one's data by plain ID, and logic that needs both lives in
  `orchestration`.
- **`<Domain>PersistenceConfiguration`** (`AnalysisPersistenceConfiguration`, …) sits in the root of
  the domain's `adapter.persistence` package and is the one place that names the schema:
  - `public static final String SCHEMA = "ANALYSIS";`, used by the entities and the Flyway bean;
  - the domain's own `@Bean(initMethod = "migrate") Flyway analysisFlyway(DataSource)`: the schema
    is Flyway's default schema (so the history table is `"ANALYSIS"."FLYWAY_SCHEMA_HISTORY"`),
    Flyway creates it (`createSchemas(true)`, no `CREATE SCHEMA` in a migration), and the location
    is derived from the configuration class's own package, never written by hand.
- **Why Boot's Flyway auto-configuration backs off, and Hibernate still waits.** Boot creates its
  `Flyway` bean only when there is none; there are three, so it backs off, and each bean migrates
  its domain when created (`initMethod`). `flyway-core` stays on the classpath: Boot treats every
  `Flyway` bean as a database initializer and makes the `EntityManagerFactory` depend on all of
  them, so Hibernate validates only after every domain is migrated.
- **Migrations live under the configuration's package** (`…/adapter/persistence/migration/`), not in
  `db/migration`: every adapter jar is on one classpath, so `classpath:db/migration` would return
  every domain's files to every domain's Flyway. Versions are per domain, from `V1`.
- **Why `schema =` stays on every entity**, written as the domain's constant
  (`@Table(name = "ANALYSES", schema = AnalysisPersistenceConfiguration.SCHEMA)`; likewise
  `@CollectionTable`). JPA and Hibernate have no per-package default schema. Rejected:
  `hibernate.default_schema` is one value per persistence unit (a persistence unit, entity manager
  factory and transaction manager per domain); an `orm.xml` per domain lists every entity;
  a naming strategy does not see the entity's package. The entity rule below fails an entity
  without it.
- **Qualified types in mappings**: `columnDefinition = "\"ANALYSIS\".\"ANALYST\"[]"` and the
  `@ColumnTransformer` cast are written qualified. At run time the search path is `"$user", public`,
  where an unqualified type does not exist, so the first insert would fail.

Giving a domain persistence:

1. `<Domain>PersistenceConfiguration` with `SCHEMA` and the Flyway bean; `flyway-core` as
   `implementation` of the adapter module.
2. The migration folder `…/adapter/persistence/migration/` with `V1__<change>.sql`, every name
   qualified.
3. A `<Domain>PersistenceConventionsTest` extending `DomainPersistenceConventionsTest`
   ([Tests](#tests)). Without it `domainPersistenceTestsCheck` fails.

## Where each class lives

```
domain/<d>/adapter/persistence/
  <Domain>PersistenceConfiguration   the domain's schema constant and Flyway bean
  Jpa<X>RepositoryAdapter     implements the port (@Component, package-private)
  <X>JpaRepository            Spring Data repository (package-private, used by its adapter only)
  entity/                     @Entity, @Embeddable, AttributeConverter: public, the root uses them
  mapper/                     MapStruct mappers entity <-> domain
```

| Kind | Suffix | Example | Place |
|---|---|---|---|
| `@Entity` | `Entity` | `AnalysisEntity`, `UserEntity` | `adapter.<port>.entity` |
| `@Embeddable` | `Embeddable` | `AnalysisSpecEmbeddable`, `UserIdEmbeddable` | `adapter.<port>.entity` |
| `AttributeConverter` | `AttributeConverter` | `UsernameAttributeConverter` | `adapter.<port>.entity` |
| Spring Data repository | `JpaRepository` | `AnalysisJpaRepository` | `adapter.persistence`, next to its adapter |
| Port implementation | `Jpa<X>RepositoryAdapter` | `JpaAnalysisRepositoryAdapter` | `adapter.persistence` |

The suffix says what a class is, so `Analysis` (domain) and `AnalysisEntity` (table) are never
confused, in code or in a stack trace. The suffix is on the class only: the table keeps the name its
migration gives it (`@Table(name = "ANALYSES", schema = AnalysisPersistenceConfiguration.SCHEMA)`),
and JPQL uses the class name
(`select a from AnalysisEntity a`).

## Mapping domain types

| Domain type | Mapping | Example |
|---|---|---|
| An ID wrapper (`PresetId`, `AnalysisId`, `UserId`, `RoleId`) | `@EmbeddedId` with an adapter-side `@Embeddable` holding one `value` on column `ID` | `@EmbeddedId PresetIdEmbeddable id` |
| Any other single-value wrapper (`Username`, `PasswordHash`, a `RoleId` in a collection) | The core type on the entity, with an `AttributeConverter` applied by `@Convert` (never `autoApply`) | `@Convert(converter = UsernameAttributeConverter.class) Username username` |
| A group of columns that is one domain value | `@Embeddable` | `AnalysisSpecEmbeddable`, `RunStatsEmbeddable` |
| An enum | A PostgreSQL enum type whose labels are the Java constant names: `@Enumerated(EnumType.STRING)`, `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, `columnDefinition = "\"ANALYSIS\".\"ANALYSIS_STATUS\""` | `AnalysisStatus status` |
| A set of values that needs no foreign key | A PostgreSQL array on the owning row: `@JdbcTypeCode(SqlTypes.ARRAY)`, `columnDefinition = "\"ANALYSIS\".\"ANALYST\"[]"`. An enum array also needs `@ColumnTransformer(write = "cast(? as \"ANALYSIS\".\"ANALYST\"[])")` (see below) | `List<Analyst> analysts`, `Set<Permission> permissions` |
| References to another aggregate | An `@ElementCollection` on a join table, so the foreign key stays; never a `@ManyToMany` between aggregates | `Set<RoleId> roleIds` on `USER_ROLES` |
| A timestamp | `Instant` on a `TIMESTAMPTZ` column; a domain fact or an audit field ([Timestamps](#timestamps-domain-facts-and-audit-fields)) | `Instant createdAt` |
| The row version | `@Version @Column(name = "VERSION", nullable = false) Long version` on a `BIGINT` column; `Long version` on the domain record, mapped by name in both directions ([Optimistic locking](#optimistic-locking)) | `Long version` |

Why these choices:

- **IDs are embeddables, not converted.** JPA does not apply an `AttributeConverter` to an `@Id`
  (Jakarta Persistence 3.2, §3.9). The core records cannot be the embeddables themselves, since the
  core has no JPA. MapStruct maps `UserId` ↔ `UserIdEmbeddable` by their `value`.
- **No enum is stored as text.** The database knows the allowed values, and a value Java does not
  know fails on write instead of on read. The labels are the constant names (`ANALYSIS_READ_ALL`),
  even where the API uses another form (`Permission#key()`, `analysis:read:all`), so no converter is
  needed.
- **An enum array carries a SQL cast.** Hibernate binds the array of an enum as `varchar[]`, which
  PostgreSQL rejects for a column of an enum-array type, so the first insert would fail even though
  `ddl-auto=validate` passes. `@ColumnTransformer(write = "cast(? as \"<DOMAIN>\".\"<ENUM_TYPE>\"[])")` on the
  field casts the bound value, with the type qualified by the domain's schema. It is the one SQL fragment allowed in production code: it is a column
  write expression inside the mapping, not a query, so the plain-SQL rule above does not cover it.
  `enums_are_not_stored_as_text` fails an enum array without it.
- **Arrays for value sets, join tables for references.** An array is read with its row, with no
  extra query and no entity graph. But an array element cannot have a foreign key, so a set of IDs
  of another aggregate (`USER_ROLES`) stays a join table: `USER_ROLES_ROLE_ID_FK` is what keeps a
  role that is still assigned from being deleted.
- **Aggregates refer to each other by ID.** Loading a user never loads its roles.

Entities are classes, as JPA needs: a public no-arg constructor, accessors for MapStruct, and no
business logic beyond copying state between two entities of the same table. Entities do not
override `equals` / `hashCode`; adapters compare domain records, never entities. The ID embeddables
do, by `value`, and are `Serializable`, as JPA requires of an `@EmbeddedId`.

## Repositories and adapters

- **Upsert** is `repository.save(entity)`: an entity with an assigned ID is merged (insert or
  update). A column that must keep its first value is `@Column(updatable = false)`. `USERS.CREATED_AT`
  is kept by `@CreatedDate` and the load-and-copy save of `JpaUserRepositoryAdapter`;
  `updatable = false` stays on it as a guard. The entity carries a `@Version`, so a `null` version
  persists (an existing ID fails) and any other version is merged and checked against the stored one
  ([Optimistic locking](#optimistic-locking)).
- **Insert only**: an entity whose insert must fail on an existing ID implements `Persistable` with
  a `@Transient` "new" flag, so `save` is a `persist` (`AnalysisEntity`).
- **Partial updates** load the managed entity in a `@Transactional` adapter method, compare the
  record's version with the entity's, and copy only the fields that may change. Dirty checking
  writes them (`JpaAnalysisRepositoryAdapter#update`).
- **A write that must reach the database in the call** ends with `repository.saveAndFlush(entity)`,
  never a `save` (or a change to a managed entity) followed by a separate `repository.flush()`: one
  call, and the returned entity is the one to map. For a managed entity it is a merge onto itself
  followed by the flush.
- **A constraint error that the port promises** surfaces in the call: `saveAndFlush` inside
  `@Transactional`. With JPA, Spring's exception translation yields a
  `DataIntegrityViolationException` for unique and foreign-key violations alike. An adapter whose
  port promises `DuplicateKeyException` translates it: it walks the cause chain for a Hibernate
  `ConstraintViolationException` of kind UNIQUE and rethrows `DuplicateKeyException`
  (`JpaUserRepositoryAdapter#save`). Move that helper to `:backend:library:persistence` once a
  second adapter needs it.
- **No N+1 queries**: a finder of an entity with an `@ElementCollection` carries
  `@EntityGraph(attributePaths = "…")`.
- **Read methods that map entities** are `@Transactional(readOnly = true)`, so a lazy collection
  never escapes a closed session (`spring.jpa.open-in-view=false`).
- **Lists with a limit** use `findBy(spec, q -> q.sortBy(…).limit(n).all())`: one query, no
  `COUNT`. A `Page` would add a count the port never needs.
- **Index-friendly lookups**: a case-insensitive lookup is JPQL `lower(u.username) = lower(:username)`,
  matching the `LOWER(...)` unique index. A derived `IgnoreCase` method would use `upper(...)` and
  miss it.

## Timestamps: domain facts and audit fields

**A timestamp is a domain fact when the domain gives it its value or decides with it**: it can
differ from the time of the write, or a rule or ordering in the core uses it. Then the core sets it
from the injected `java.time.Clock`. **Otherwise it is an audit field**: it records when the row was
written, and Spring Data JPA auditing sets it (`@CreatedDate`, `@LastModifiedDate`).

| Column | Kind | Why |
|---|---|---|
| `ANALYSES.CREATED_AT` | Domain fact | An imported run takes the start time of the original run, and recovery orders queued analyses by it. Set by the analysis core from the `Clock` |
| `USERS.CREATED_AT` | Audit field (`@CreatedDate`) | When the row was inserted; no rule reads it |
| `USERS.UPDATED_AT` | Audit field (`@LastModifiedDate`) | When the row was last written; no rule reads it |
| `PRESETS.UPDATED_AT` | Audit field (`@LastModifiedDate`) | When the preset was last saved. It is shown to the user, but it only records the write |

- **One `Clock` bean.** `TradingPlatformApplication` defines `Clock.systemUTC()`; cores and adapters
  inject `java.time.Clock`, which is neither a persistence nor a Spring type, so the core rule holds.
  A test passes a fixed clock instead of calling `Instant.now()`.
- **`ClockDateTimeProvider`** (`:backend:library:persistence`) feeds auditing from that `Clock`,
  truncated to microseconds, which `TIMESTAMPTZ` keeps: the value returned from `save` equals the one
  read back later. `JpaAuditingConfiguration` enables auditing with it.
- **Every entity with an audit field** has `@EntityListeners(AuditingEntityListener.class)`.
- **The records keep audited fields for reading.** `Preset.updatedAt`, `User.createdAt` and
  `User.updatedAt` are `null` on a record the core builds before its first save, and set on every
  record a port returns: `save` returns the stored record, mapped from the entity after the flush.
- **The mappers ignore audited fields** (`@Mapping(target = "…", ignore = true)`): an adapter never
  writes one from the domain.
- **Presets** are saved with `saveAndFlush` (a merge). The incoming entity has `updatedAt == null`,
  so the row is always dirty and every save sets `UPDATED_AT`, also with unchanged content.
- **Users** are not merged: a merge would copy the incoming `createdAt == null` onto the managed
  entity. `JpaUserRepositoryAdapter#save` loads the managed entity by ID. If it exists, it copies the
  domain fields and role IDs onto it and clears `updatedAt`, so the flush always updates the row and
  auditing sets the time. If not, it persists the new entity.
- **`@CreatedBy` / `@LastModifiedBy`** are the way to record who wrote a row. They need
  authentication and new columns, so they are added with the login, not before.

## Optimistic locking

A write based on a stale copy of a row fails instead of overwriting a newer one.

- **A `VERSION BIGINT NOT NULL DEFAULT 0` column** on every table that is updated in place
  (`ANALYSES`, `PRESETS`, `USERS`, `ROLES`), and `@Version @Column(name = "VERSION", nullable = false)
  Long version` on its entity. A wrapper type, so Spring Data treats `version == null` as new. A
  collection table (`USER_ROLES`) has no column: Hibernate increments the owning row's version when
  the collection changes.
- **The version is a field of the domain record** (`Long version` on `Analysis`, `Preset`, `User`,
  `Role`): `null` on a record the core builds before its first save, set on every record a port
  returns. Persistence owns it: the core copies it through its transitions and never changes it.
  It travels through the core, not only the adapter, because the races to catch are between a read
  in one call and a write in a later one; a check inside the adapter's own transaction would cover
  only the milliseconds between its read and its write. A plain `Long` keeps persistence types out
  of the core.
- **The port promises** (Javadoc on each write method): a record whose version differs from the
  stored row's fails with `org.springframework.dao.OptimisticLockingFailureException` and leaves the
  row unchanged; a `null` version means insert, so it fails on an existing ID too. Spring's
  exception translation already yields `ObjectOptimisticLockingFailureException` (a subclass) for
  Hibernate's stale-state errors.
- **Writes return the stored record**, with its new version, so a caller never keeps an outdated
  one. `insert` stays `void`.
- **How the adapters check it**:
  - Merged entities (`JpaPresetRepositoryAdapter`, `JpaRoleRepositoryAdapter`): `saveAndFlush`;
    Hibernate rejects a detached entity whose version differs from the stored one.
  - Managed entities (`JpaUserRepositoryAdapter#save`, `JpaAnalysisRepositoryAdapter#update` and
    `#replaceImported`): the adapter loads the entity, compares the incoming version with the
    entity's and throws `OptimisticLockingFailureException` on a mismatch (a `null` version
    included, and a missing row with a non-null version: it was deleted meanwhile), because JPA
    forbids changing the version of a managed entity. It then copies the fields and calls
    `saveAndFlush`; the `UPDATE … WHERE VERSION = ?` covers the window between the load and the
    flush. The compare is a private helper per adapter, moved to `:backend:library:persistence`
    once a third needs it.
- **The services translate it** where they call a write: a domain error code `CONCURRENT_UPDATE`
  (`AnalysisError`, `SettingsError`) with the ID as parameter, and the BFF maps it to HTTP 409
  (`concurrent_update`). A domain whose ports promise the exception but no service writes yet
  (identity) gets its code with the first service that writes.
- **Where the API exposes a write**, the request carries the version (`SavePresetRequest.version`,
  optional: without it the write applies to what is stored), and the frontend sends the one it read.

## Configuration

In `artifact/backend/src/main/resources/application.properties`:

| Property | Value | Why |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | Flyway owns the schema |
| `spring.jpa.open-in-view` | `false` | No session held open for the web request |
| `spring.jpa.show-sql` | `false` | No SQL in the log |
| `spring.jpa.hibernate.naming.physical-strategy` | `org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl` | Spring Boot's default strategy lower-cases every name, quoted ones too |
| `spring.jpa.properties.hibernate.globally_quoted_identifiers` | `true` | The uppercase names in `@Table` / `@Column` reach PostgreSQL quoted, as written |

The adapter tests load the same JPA settings from `jpa-test.properties` in the test fixtures of
`:backend:library:persistence`. Keep the two in sync. The application's Spring Boot tests run on the
real file, so a drift shows up there.

## Shared persistence code

`:backend:library:persistence` holds technical persistence code that more than one adapter needs.
Like every shared library, it holds no domain type and no business rule
([package structure](backend-java-package-structure.md#shared-libraries)):

- **Main source set**: `JpaAuditingConfiguration` and `ClockDateTimeProvider`
  ([Timestamps](#timestamps-domain-facts-and-audit-fields)). A base type that a second adapter needs
  (an insert-only `Persistable` base, a generic `AttributeConverter`, a `@NoRepositoryBean` base
  repository) moves here, never copied. There is no `@MappedSuperclass` for timestamps: `PRESETS`,
  `ANALYSES` and `USERS` do not share their timestamp columns, and inheritance would only couple them.
- **Test fixtures** (Gradle's `java-test-fixtures`): the shared test code of the persistence
  adapters (`@JpaAdapterTest`, `MutableTestClock`, the abstract `DomainPersistenceConventionsTest` and
  the checks it delegates to, see [Tests](#tests)), used with
  `testImplementation(testFixtures(project(":backend:library:persistence")))`.
  Gradle keeps test fixtures off every main classpath, and the ArchUnit tests do not import them
  (`DoNotIncludeTestFixtures`).

## Tests

- Every repository is tested against PostgreSQL (Testcontainers), through its port.
- **`@JpaAdapterTest`** (test fixtures) wires Hibernate, Spring Data JPA repositories and
  transactions as in the application, with `jpa-test.properties`. The test's own `Config` provides:
  - the data source, from `PostgresTestDatabase`: one container per test JVM, a fresh database per
    context. The migrations come from the domain's own `<Domain>PersistenceConfiguration`, which the
    component scan picks up: its Flyway bean migrates before Hibernate validates, as in the
    application;
  - `@AutoConfigurationPackage`, which keeps the entity and repository scan in the module;
  - a `@ComponentScan` of its adapter and mappers.

  It also imports `JpaAuditingConfiguration` and a `MutableTestClock` as the `Clock` bean (a fixed
  start instant, `set(Instant)` and `advance(Duration)`), so every adapter test is wired as the
  application is, with a time it controls. Audited columns are asserted against that clock, on insert
  and on update.

  Each module's test therefore also validates its entities against its migrations.
- **No test-managed transaction.** No repository test is `@Transactional`, and `@DataJpaTest` is not
  used. Every port call runs in its own transaction as in production, so a read after a write goes
  to the database, not to the persistence context. Otherwise "creation time kept" or "duplicate
  username fails" could pass without the database ever seeing the statement.
- **No `JdbcClient` in tests either.**
  - Checks go through the ports and the package-private repositories. The exception is the catalog
    checks of the conventions test (`DatabaseNamingCheck`, `DomainMigrationIsolationCheck`; see
    [database naming](backend-database-naming.md)): the catalog has no entities, so they read it
    with plain JDBC (`java.sql`). `org.springframework.jdbc` stays banned.
  - Column types are covered by `ddl-auto=validate`.
  - A migration test seeds old-form rows with a test-only Flyway migration in an extra location of
    its own (under `src/test/resources`), between the real versions. Its own `Flyway` bean lists both locations, and
    the scan leaves out the domain's `*PersistenceConfiguration` (`V2SettingsVersionMigrationTest`).
- **A stale write per entity**: each repository test reads a record twice, writes the first copy
  (the version rises by one), then writes the stale second copy and expects
  `OptimisticLockingFailureException`; a read afterwards returns the first write unchanged. A new
  record starts at version 0, and saving a `null`-version record with an existing ID fails.
- **`DomainPersistenceConventionsTest`** (abstract, test fixtures): each domain with persistence
  extends it once, naming its configuration class, its schema and its Flyway bean method, so it
  checks the domain's real configuration. Its four tests:
  - `entitiesAreMappedToTheDomainSchema` (ArchUnit, `EntitySchemaRule`): every `@Entity` has
    `@Table(schema = …)` of the domain, every `@CollectionTable` / `@JoinTable` too, and every
    quoted name in a `columnDefinition` or `@ColumnTransformer` is qualified with the domain's
    schema. It fails when the package holds no entity;
  - `migrationsStayInTheDomainSchema` (`DomainMigrationIsolationCheck`): migrates the domain alone in
    a fresh database, with a decoy default schema, so an unqualified `CREATE` lands in the decoy and
    is reported, and a reference to another domain fails because that schema does not exist; the
    catalog must then hold every object in the domain's schema and no other schema;
  - `theDomainFlywayCreatesTheSchemaWithItsHistory`: the domain's Flyway bean creates the schema
    with its `FLYWAY_SCHEMA_HISTORY` and at least one table, and `public` holds no history table;
  - `namesFollowTheConvention` (`DatabaseNamingCheck`): the naming rules, on the domain's schema.
- On a Podman machine (macOS, Windows), the build points Testcontainers' Ryuk at the socket inside
  the machine's VM (`build-logic`, `tradinganalysisplatform.java-library`), so the tests run on
  Docker and Podman alike without local settings.

## Where it is checked

The ArchUnit rules are in
[`PersistenceArchitectureTest`](../../artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java),
except `domain_core_does_not_depend_on_infrastructure`, which is in `ArchitectureTest`.


| Rule | Check |
|---|---|
| No `org.springframework.jdbc`, `java.sql`, `javax.sql` in production code (`DataSource` allowed in `*PersistenceConfiguration`) | `PersistenceArchitectureTest.production_code_does_not_use_plain_sql` |
| No `org.springframework.jdbc` in test code | Checkstyle `IllegalImport` (`config/checkstyle/checkstyle.xml`) |
| No native query | `PersistenceArchitectureTest.no_native_queries` |
| No separate `repository.flush()`: writes end with `saveAndFlush` | `writes_use_save_and_flush` |
| Entities, embeddables, converters in `adapter.<port>.entity`, and nothing else there | `entities_live_in_entity_packages`, `entity_packages_hold_only_entities` |
| Spring Data repositories next to their adapter | `spring_data_repositories_live_in_persistence_roots`, `port_package_roots_hold_only_adapters` |
| Suffixes `Entity`, `Embeddable`, `AttributeConverter`, `JpaRepository` | `persistence_classes_are_named_by_kind` |
| Enums as PostgreSQL enum types or arrays, never text; an enum array carries its `@ColumnTransformer` cast | `enums_are_not_stored_as_text` |
| An entity with an auditing field has `@EntityListeners(AuditingEntityListener.class)` | `audited_entities_have_the_auditing_listener` |
| Every entity has a `@Version` field | `entities_have_a_version` |
| The core has no persistence dependency | `domain_core_does_not_depend_on_infrastructure` |
| Entities match their migrations | `ddl-auto=validate` in the adapter tests and the Spring Boot tests |
| Entities mapped to the domain's schema, types qualified | `entitiesAreMappedToTheDomainSchema` of the domain's `<D>PersistenceConventionsTest` |
| Migrations create everything in the domain's schema, qualify every name, reference no other domain | `migrationsStayInTheDomainSchema`, `theDomainFlywayCreatesTheSchemaWithItsHistory` |
| Names follow [database naming](backend-database-naming.md), the schema name included | `namesFollowTheConvention` |
| Each of those checks fails on a violation | The library's `EntitySchemaRuleTest`, `DomainMigrationIsolationCheckTest`, `DatabaseNamingCheckTest` and `SamplePersistenceConventionsTest`, against fixtures of a made-up domain |
| A domain with persistence (an `@Entity` or a migration) has a conventions test | `:backend:domainPersistenceTestsCheck` (part of `:backend:check`, `mise run build` and `mise run check`) |
| Only `<Domain>PersistenceConfiguration` is a `@Configuration` in a domain adapter, in `adapter.persistence` | `ArchitectureTest.domain_adapter_configurations_are_persistence_configurations` (`adapter_components_implement_an_outbound_port` and `port_package_roots_hold_only_adapters` only exempt it) |
