# Plan: Persistence through Spring Data JPA with Hibernate

- **Issue**: #112 (Persistence: migrate from JdbcClient to Spring Data JPA with Hibernate)
- **Plan**: 1 of 1 for this issue; depends on: none (#113 and #79 are merged)
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md): the
  developer's decision. The schema changes (PostgreSQL enum types, enum arrays, `ROLE_PERMISSIONS`
  folded into `ROLES`), and an older release cannot run on the migrated database.

## Goal

Every backend adapter that stores data goes through Spring Data JPA with Hibernate instead of
handwritten SQL through `JdbcClient`. There are JPA entities, embeddables and Spring Data
repositories in place of the `*Row` records and SQL strings. The schema stays with Flyway and is
validated by Hibernate at startup (`ddl-auto=validate`). Plain SQL in production code is forbidden.
`docs/coding-convention/backend-java-persistence.md` documents the rule and `ArchitectureTest`
enforces it. The schema becomes typed: enum columns are PostgreSQL enum types, and value sets are
PostgreSQL arrays. Two new migrations convert existing databases without data loss. Users see no
difference in the API or the UI.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend: `domain/{settings,analysis,identity}/adapter` | `backend-java-package-structure.md`: one package per port (`adapter.persistence`), the adapter at the port package root, sub-packages of known kinds only (`entity` replaces `row`, `mapper` stays), MapStruct `SourceToTargetMapper` with one `map` method, package-private unless another package needs it. `backend-database-naming.md`: uppercase quoted names, explicit constraint names, Flyway owns the schema, never edit an applied migration. `backend-java-checkstyle.md` / `backend-java-formatting.md`: Checkstyle and Spotless pass, suppressions only in place with a reason |
| backend: `domain/*/core` | No change. The cores keep no persistence dependency (`domain_core_does_not_depend_on_infrastructure`, extended to `jakarta.persistence..` and `org.hibernate..`) |
| backend: `artifact/backend` (app, `ArchitectureTest`) | `docs/coding-convention/README.md` "Changing a convention": the document, the ArchUnit rule and the code change together, and every new rule is shown to fail on a deliberately misplaced class before merging |
| build: `gradle/libs.versions.toml`, adapter `build.gradle.kts` | Versions come from the Spring Boot BOM (no version in the catalog entries) |
| docs | `docs/coding-convention/` (new persistence document, package-structure and database-naming updates, README index and enforcement table) |

## Design

### Dependencies and configuration

- **No `spring-jdbc` and no `spring-boot-starter-jdbc` declared anywhere.**
  - The catalog entries `spring-jdbc` and `spring-boot-starter-jdbc` are removed. Catalog
    additions: `spring-boot-starter-data-jpa`, `spring-data-jpa`, `jakarta-persistence-api`,
    `hibernate-core` (for `@JdbcTypeCode`), all BOM-managed.
  - `spring-jdbc` still arrives at runtime, transitively: Spring Data JPA needs `spring-orm`, whose
    `JpaTransactionManager` and exception translation are built on `spring-jdbc`. It cannot be
    removed, only left unused. Its use is banned:
    - in production code by ArchUnit (`production_code_does_not_use_plain_sql`);
    - in test code by Checkstyle (`IllegalImport` gets `org.springframework.jdbc`, which runs on
      `checkstyleMain` and `checkstyleTest`).
- `:backend`: `spring-boot-starter-data-jpa` replaces `spring-boot-starter-jdbc`.
- Adapter modules (settings, analysis, identity) use `implementation(libs.spring.data.jpa)`,
  `implementation(libs.jakarta.persistence.api)` and `implementation(libs.hibernate.core)` instead
  of `spring-jdbc`. They need no Spring Boot dependency in `main`. The tests get JPA through the
  test fixtures of `:backend:library:persistence` (see Shared persistence code).
- `application.properties` (new `# --- Persistence (JPA) ---` block next to the datasource):
  - `spring.jpa.hibernate.ddl-auto=validate`: Hibernate never creates or alters tables, and an entity
    that does not match its migration fails at startup.
  - `spring.jpa.open-in-view=false`
  - `spring.jpa.show-sql=false` (SQL logging off, stated explicitly)
  - `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl`
    and `spring.jpa.properties.hibernate.globally_quoted_identifiers=true`. Spring Boot's default
    strategy (`CamelCaseToUnderscoresNamingStrategy`) lowercases every name, quoted ones too. With the
    standard strategy and global quoting, the uppercase names written in `@Table`, `@Column`,
    `@CollectionTable` and `@JoinColumn` reach PostgreSQL as written (`"ANALYSES"."CREATED_AT"`).
    Every mapped table and column therefore names its database name explicitly.
- Timestamps need no change. Every timestamp column is already `TIMESTAMPTZ` (since #113), and
  entities hold `Instant`. The `OffsetDateTime` ↔ `Instant` MapStruct converters and their
  `TimestampMappersTest` go away.

### Schema changes: enum types and arrays (new migrations)

"No string type in the database for an enum": every column that holds a Java enum becomes a
PostgreSQL enum type. Every set of enum values becomes an array of that type. Free text stays
`TEXT`: ticker, LLM names, output language, decision, error code and message, the refs, the preset
payload and name, username, password hash. Two new migrations, numbered globally and named per
`backend-database-naming.md`:

- **`V4__analysis_enum_types.sql`** (analysis adapter):
  - creates `"ANALYSIS_STATUS"`, `"ANALYSIS_SOURCE"`, `"ASSET_TYPE"`, `"RATING"` and `"ANALYST"`.
    Labels are the Java constant names, in declaration order.
  - `ALTER COLUMN … TYPE "ANALYSIS_STATUS" USING "STATUS"::"ANALYSIS_STATUS"` (and the same for
    `SOURCE`, `ASSET_TYPE`, `RATING`). `ANALYSES_STATUS_IDX` is rebuilt by PostgreSQL.
  - `"ANALYSTS"` becomes `"ANALYST"[] NOT NULL`:
    `USING COALESCE(NULLIF(regexp_split_to_array(trim("ANALYSTS"), '\s*,\s*'), '{""}'), '{}')::"ANALYST"[]`.
    An empty string becomes `{}`. The order is kept as stored, which is the spec's sorted order.
  - Comments on each type say which Java enum it mirrors.
- **`V5__identity_permission_array.sql`** (identity adapter):
  - creates `"PERMISSION"` with the Java constant names as labels (`ANALYSIS_RUN`, …), not the keys.
    `Permission#key()` (`analysis:run`) stays the API and wire form.
  - adds `"ROLES"."PERMISSIONS" "PERMISSION"[] NOT NULL DEFAULT '{}'`.
  - fills it from `ROLE_PERMISSIONS`, converting each key to its label with
    `UPPER(REPLACE("PERMISSION", ':', '_'))`, which holds for every key today: `analysis:read:all`
    becomes `ANALYSIS_READ_ALL`, `settings:keys:write` becomes `SETTINGS_KEYS_WRITE`. A key that
    has no label fails the migration instead of being dropped.
  - drops `"ROLE_PERMISSIONS"` (its foreign key and primary key go with it).
  - `USER_ROLES` stays a join table. An array element cannot have a foreign key, and
    `USER_ROLES_ROLE_ID_FK` is what keeps a role that is still assigned from being deleted.
- Hibernate mapping:
  - an enum column is `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)` with
    `@Column(name = "STATUS", columnDefinition = "\"ANALYSIS_STATUS\"")`.
  - an enum array is `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.ARRAY)` on a `List` or
    `Set` of the enum, with `columnDefinition = "\"ANALYST\"[]"`.
  - If Hibernate 7 binds or validates a named enum by a type name derived from the Java class
    (`AnalysisStatus`) instead of the column definition, a generic technical
    `UppercaseNamedEnumJdbcType` goes into the main source set of `:backend:library:persistence`. It
    derives `"ANALYSIS_STATUS"` from the class name (upper snake case, quoted) and holds no domain
    type. The repository tests with `ddl-auto=validate` decide whether it is needed, and the pull
    request says which way it went.
- Existing databases: both migrations convert data in place, without loss. Migration tests prove it
  (see Tests).

### Mapping decisions (written into the convention)

- **Single-value wrappers**: IDs are embeddables, every other wrapper uses an attribute converter.
  - **IDs** (`PresetId`, `AnalysisId`, `UserId`, `RoleId`) are `@EmbeddedId`. Each has an
    adapter-side `@Embeddable` (`PresetIdEmbeddable`, `AnalysisIdEmbeddable`, `UserIdEmbeddable`,
    `RoleIdEmbeddable`) with one `value` field on column `ID`. JPA does not apply an
    `AttributeConverter` to an `@Id` (Jakarta Persistence 3.2, §3.9), so an embeddable is the
    portable way to keep the ID typed in the entity. The core records cannot be the embeddables
    themselves: the core has no JPA. MapStruct maps `UserId` ↔ `UserIdEmbeddable` by their `value`
    property, with no hand-written scalar mapper. The repositories are typed with the embeddable
    (`JpaRepository<UserEntity, UserIdEmbeddable>`).
  - **Other wrappers** (`Username`, `PasswordHash`, and `RoleId` inside `USER_ROLES`) are held as the
    core type on the entity, with an `AttributeConverter` in the `entity` package
    (`UsernameAttributeConverter`, `PasswordHashAttributeConverter`, `RoleIdAttributeConverter`),
    applied with `@Convert`, not `autoApply`.
  - The `StringTo*IdMapper` / `StringToUsernameMapper` / `StringToPasswordHashMapper` scalar mappers
    of the persistence adapters go away. The BFF's `StringToAnalysisIdMapper` stays.
- **Enums**: the core enum type on the entity field, mapped to its PostgreSQL enum type (see
  Schema changes). No enum is stored as text, and no enum needs a converter: the labels are the
  constant names.
- **`@Embeddable`** groups columns that belong together in the domain model: in `AnalysisEntity`,
  `AnalysisSpecEmbeddable` (ticker … checkpoint enabled, the `AnalysisSpec` columns) and
  `RunStatsEmbeddable` (LLM/tool calls, tokens in/out, cost, elapsed ms, the `RunStats` columns).
  MapStruct then maps `spec` ↔ `spec` and `stats` ↔ `stats` without the 18 flattening `@Mapping`
  lines of `AnalysisToAnalysisRowMapper`. `ANALYSTS` is a `List<Analyst>` on the `"ANALYST"[]` array,
  so `StringToAnalystListMapper` / `AnalystListToStringMapper` go away. `COST_USD` stays `Double` in the entity (`DOUBLE PRECISION`), and MapStruct's built-in conversion
  maps it to `BigDecimal` as today.
- **Collections**:
  - A set of values that needs no foreign key is a PostgreSQL array on the owning row: `ANALYSTS`
    (`List<Analyst>`) and `ROLES.PERMISSIONS` (`Set<Permission>`). It is read with the row, so there
    is no extra query and no entity graph.
  - A set of references to another aggregate is an `@ElementCollection` on a join table, so the
    foreign key stays. `USER_ROLES` is `Set<RoleId>` on `UserEntity`
    (`@CollectionTable(name = "USER_ROLES", joinColumns = @JoinColumn(name = "USER_ID"))`,
    `@Column(name = "ROLE_ID")`, `RoleIdAttributeConverter`), not a `@ManyToMany`. The user
    aggregate keeps referring to roles by ID, as `User#roleIds()` does, and loading a user never
    loads roles.
- **Entities are classes** (JPA needs a no-arg constructor and non-final fields): a `protected`
  no-arg constructor, accessors for MapStruct, no business logic beyond the copy methods named below.
  `equals`/`hashCode` are not overridden. The adapters compare domain records, never entities.
- **Naming** (documented in the persistence convention and enforced by ArchUnit,
  `persistence_classes_are_named_by_kind`). The suffix says what a class is, so `Analysis` (domain)
  and `AnalysisEntity` (table) are never confused, in code or in a stack trace:

  | Kind | Suffix | Example |
  |---|---|---|
  | `@Entity` | `Entity` | `AnalysisEntity`, `UserEntity` |
  | `@Embeddable` | `Embeddable` | `AnalysisSpecEmbeddable` |
  | `AttributeConverter` | `AttributeConverter` | `UsernameAttributeConverter` |
  | Spring Data repository | `JpaRepository` | `AnalysisJpaRepository` |
  | Port implementation | `Jpa<X>RepositoryAdapter` | `JpaAnalysisRepositoryAdapter` |

  The entity suffix is on the class only. The table name stays what the migration says
  (`@Table(name = "ANALYSES")`), and JPQL uses the class name (`select a from AnalysisEntity a`).

### Shared libraries: `:backend:library:<library>`, starting with `:backend:library:mapper`

Today `backend-java-package-structure.md` ("Shared converters and accepted duplicates") keeps a copy
of a converter in each module that needs it. This plan replaces that with a shared library at
backend level, as the developer asked. What is duplicated today, and what happens to it:

| Duplicate | Copies | Outcome |
|---|---|---|
| `InstantToOffsetDateTimeMapper`, `OffsetDateTimeToInstantMapper` | analysis, settings, identity persistence | Deleted: entities hold `Instant`, nothing to convert |
| `DurationToMillisMapper` | `bff.delegate.impl.mapper`, analysis `persistence.mapper` (identical) | Moves to `library.mapper`, both copies deleted |
| `RunEventTypeToStringMapper` | `bff.delegate.impl.mapper`, analysis `eventstore.mapper` (identical: `name()` in lower case) | Replaced by a generic `EnumToLowerCaseNameMapper` in `library.mapper` (`<E extends Enum<E>> String map(E source)`, `@Named("lowerCaseName")`), used with `qualifiedByName = "lowerCaseName"` only on the event-type mappings, so no other enum → `String` mapping changes. Both copies deleted |
| `StringToAnalysisIdMapper` | `bff.delegate.impl.mapper`, analysis `persistence.mapper` | Not a real duplicate (the BFF copy turns an invalid ID into a 404). The adapter copy goes away anyway, because IDs become `@EmbeddedId`s (see Mapping decisions), so no clash is left |

**Where the library goes.** Alternatives considered:

1. **A family of shared Gradle modules under `:backend:library` (chosen).** `:backend:library` is a
   parent folder with no code of its own, like `:backend:domain`. Each shared library under it is
   its own Gradle module with its own root package, so a module depends only on the libraries it
   uses:

   | Gradle project | Folder | Root package | Holds |
   |---|---|---|---|
   | `:backend:library:mapper` (this plan) | `artifact/backend/library/mapper` | `….library.mapper` | Generic MapStruct scalar mappers |
   | `:backend:library:<library>` (later) | `artifact/backend/library/<library>` | `….library.<library>` | One kind of generic code each, e.g. a shared test or Docker support library |

   `:backend:library:mapper` applies the `tradinganalysisplatform.mapstruct` plugin and depends on no
   project module. Modules that need it add `implementation(project(":backend:library:mapper"))`:
   today `bff:impl` and `domain:analysis:adapter`. The classpath guarantees that a library
   cannot reach domain, BFF or orchestration code, so sharing it couples no two domains.
   `domains_are_independent` stays as it is (libraries are not domain slices). A library
   may depend on another library, never on anything else of the project. Adding one
   means a new module, a row in the package-structure document's Modules table, and a placement
   rule for its kind of class in `ArchitectureTest`.
2. A `mapper.common` package inside each module (the current rule for sharing within one module).
   It does not help across modules.
3. Put the generic mappers in a domain core, or in `bff:impl` and depend on that. This breaks the
   layering: cores have no MapStruct and adapters must not depend on the BFF.
4. A shared MapStruct `@MapperConfig(uses = …)` in `:backend:library:mapper`. It still needs option 1, and
   it hides which converters a mapper uses. Not chosen. Mappers keep listing their `uses` explicitly.
5. Remove the need instead: a JPA `AttributeConverter` `Duration` ↔ millis on the entity would leave
   only the BFF copy. It does not cover the event-type duplicate, and it moves a unit conversion
   (millis) into the entity mapping. Not chosen.

**What may go into a shared library: technical code only, never domain logic.** A library holds
generic, technical building blocks (scalar converters, test infrastructure, later perhaps
persistence base types) with no project types, no domain concept and no business rule, each library
for one kind of code. Anything that knows about an analysis, a user, a preset or any other domain
term stays in its domain, even when two modules end up with similar code. Such a duplicate is
accepted and listed, never moved into a library. `RunEventTypeToStringMapper` qualifies only
because its replacement, `EnumToLowerCaseNameMapper`, knows nothing about run events. The wire name
"lower-case enum name" is a technical rule, and the mapping that applies it to `RunEventType` stays
in the domain and BFF mappers. `:backend:library:mapper` holds MapStruct mappers
only (no Spring beans of its own beyond MapStruct's generated ones). Today that is the two mappers
above. A generic converter that one module needs stays in that module. Once a second module
needs it, it moves to `:backend:library:mapper` and is never copied. `MillisToDurationMapper` and identity's
`Optional<Instant>` ↔ `Instant` mappers therefore stay where they are. The `DockerClients` /
`RunnerKind` duplicates (runner `support`, not mappers) are out of scope and stay in the
accepted-duplicates table.

The generated `library.mapper` beans are found by the application's component scan (same root
package). The adapter-module tests add the package to their `@ComponentScan`.

### Shared persistence code: `:backend:library:persistence` and its test fixtures

What the three persistence adapters (and the app's Spring Boot tests) would otherwise share:

| Candidate | Today / after the migration | Decision |
|---|---|---|
| `PostgresTestDatabase` (Testcontainers container, a fresh database per context) | Four identical copies: `artifact/backend` tests and the settings, identity and analysis adapter tests | Moves to the **test fixtures of `:backend:library:persistence`**, all four copies deleted |
| Flyway setup in the test `Config` (`FLYWAY_SCHEMA_HISTORY`, `baselineVersion` so only the module's own migration runs) | Three near-identical blocks | A helper in the same library: `TestMigrations.migrate(DataSource, String baselineVersion)` |
| JPA test wiring: Boot's Hibernate / Spring Data JPA / transaction auto-configurations, the entity and repository scan, the JPA properties (`ddl-auto=validate`, naming strategy, global quoting), no test-managed transaction | Would be written three times | A meta-annotation `@JpaAdapterTest` in the same test fixtures, plus `jpa-test.properties` with the JPA properties. A module's test class (or `AdapterTestSupport`) carries `@JpaAdapterTest` and its own `Config` (data source from `PostgresTestDatabase`, `TestMigrations`, `@AutoConfigurationPackage`, `@ComponentScan` of its adapter and mappers) |
| JPA properties in `application.properties` and in `jpa-test.properties` | Two copies of four lines | Accepted: Spring Boot reads `application.properties` only from the app, and loading a shared file into it (`@PropertySource`, an `EnvironmentPostProcessor`) costs more than four lines. The app's Spring Boot tests run on the real file, so a drift shows up there. Both files say so in a comment |
| `@MappedSuperclass` for audit timestamps | `PRESETS` has `UPDATED_AT` only, `ANALYSES` `CREATED_AT` only, `USERS` both | None: the tables do not share columns, and entity inheritance would only couple them |
| Insert-only `Persistable` base, generic `AttributeConverter`s, a `@NoRepositoryBean` base repository, duplicate-key translation | One user each (analysis; none; none; identity). The wrapper converters and ID embeddables are domain types, so they stay in their domain | Not created now. When a second adapter needs one, it moves to the **main source set of `:backend:library:persistence`** (depends on `jakarta.persistence-api` and `spring-data-jpa` only, no project types), never copied. The persistence convention says so |

**`:backend:library:persistence`** (`artifact/backend/library/persistence`, package
`….library.persistence`) applies Gradle's
[`java-test-fixtures`](https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures)
plugin. Its main source set holds only technical persistence code: the `UppercaseNamedEnumJdbcType`
if Hibernate needs it (see Schema changes), and later the base types of the row above, once a
second adapter needs one. Otherwise it stays empty. The shared test code lives in its test fixtures (`src/testFixtures/java`,
`src/testFixtures/resources`): `PostgresTestDatabase`, `TestMigrations`, `@JpaAdapterTest`,
`jpa-test.properties`. The fixtures depend on Testcontainers PostgreSQL, Flyway, the PostgreSQL
driver, `spring-boot-starter-data-jpa` and `spring-boot-test` (`testFixturesApi`, so the consumers'
tests get them). Consumers declare
`testImplementation(testFixtures(project(":backend:library:persistence")))`: the three adapter
modules and `:backend`.

Why test fixtures, not a separate test-only module:

- Gradle keeps them off every main classpath. Production code cannot compile against them, so no
  ArchUnit rule is needed for that.
- They sit next to the persistence code they support, in one module, instead of a `*-test`
  sibling.
- They are not production code. `ArchitectureTest` imports with an extra `ImportOption` that skips
  test-fixture classes (the `-test-fixtures.jar` and `build/classes/java/testFixtures`), next to
  `DoNotIncludeTests`. So `production_code_does_not_use_plain_sql` needs no exception for
  `PostgresTestDatabase`'s `java.sql.DriverManager`.

The same pattern is the rule for any later shared test code: test fixtures of the library (or
module) whose code it supports, never a copied class and never a test-only main module. The
persistence convention and the package-structure document say so.

### Packages (per adapter, `domain.<d>.adapter.persistence`)

```
persistence/
  Jpa<X>RepositoryAdapter        implements the port (@Component, package-private)
  <X>JpaRepository               Spring Data interface (package-private, next to its adapter)
  entity/   <X>Entity, *Embeddable, *AttributeConverter   (public: the root package uses them)
  mapper/   <X>ToEntityMapper-style MapStruct mappers and scalar converters
```

The repositories sit next to the adapter, at the port package root, so they can stay
package-private as the issue asks. `port_package_roots_hold_only_adapters` is widened to allow
Spring Data repository interfaces there. The new rule `spring_data_repositories_live_in_persistence_roots`
keeps them nowhere else.

### settings

- `PresetEntity` (`"PRESETS"`: `@EmbeddedId PresetIdEmbeddable id`, `NAME`, `PAYLOAD`, `UPDATED_AT`),
  `PresetJpaRepository extends JpaRepository<PresetEntity, PresetIdEmbeddable>`.
- `JpaPresetRepositoryAdapter`: `findAll`, `findById` map entities to `Preset`. `save` is an upsert
  through `repository.save(entity)` (an assigned ID means `merge`: insert or update). `delete`
  returns whether a row went away: a `@Modifying` JPQL
  `@Query("delete from PresetEntity p where p.id = :id") int deleteByIdCounting(PresetIdEmbeddable id)` (one
  statement, the count it returns is the answer), called from a `@Transactional` adapter method.
- Mappers: `PresetToPresetEntityMapper`, `PresetEntityToPresetMapper`. Delete `PresetRow`, the row
  mappers, the `OffsetDateTime` converters and `StringToPresetIdMapper`.

### analysis

- `AnalysisEntity` (`"ANALYSES"`) with `@EmbeddedId AnalysisIdEmbeddable id`,
  `@Embedded AnalysisSpecEmbeddable spec` (asset type and analysts as enum / enum array),
  `@Embedded RunStatsEmbeddable stats` and the remaining columns (status, source, rating as enums).
  It implements `Persistable<AnalysisIdEmbeddable>` with a `@Transient` "new" flag (true when built by the mapper, false after
  `@PostLoad`/`@PostPersist`). `insert` is then a plain `persist`, which fails on an existing ID as
  the `INSERT` did, without the `SELECT` a `merge` would add.
- `AnalysisJpaRepository extends JpaRepository<AnalysisEntity, AnalysisIdEmbeddable>,
  JpaSpecificationExecutor<AnalysisEntity>`, with derived `findByExternalRef(String)`,
  `findByIdAndSource(AnalysisIdEmbeddable, AnalysisSource)` and `findByStatusIn(Collection<AnalysisStatus>)`.
- `JpaAnalysisRepositoryAdapter`:
  - `insert`: `repository.save(toEntity.map(analysis))`.
  - `update` (`@Transactional`): load by ID or throw
    `IllegalStateException("No analysis <id> to update")` as today. Then copy only the run state from
    the mapped entity onto the managed one with `AnalysisEntity#applyRunState(AnalysisEntity)`:
    status, rating, decision, stats, startedAt, endedAt, errorCode, errorMessage, runnerRef. Spec,
    createdAt, source and externalRef stay as stored, as the `UPDATE` did. Dirty checking writes it.
  - `replaceImported` (`@Transactional`): `findByIdAndSource(id, EXTERNAL)` or throw
    `IllegalStateException("No imported analysis <id> to replace")`. Then
    `AnalysisEntity#replaceImported(AnalysisEntity)` copies everything except ID, source and
    runnerRef, as `REPLACE_IMPORTED` did.
  - `findAll(filter)`: a `Specification` built from the non-null filter fields (status equals,
    ticker equals), run as `repository.findBy(spec, q -> q.sortBy(Sort.by(DESC, "createdAt"))
    .limit(LIST_LIMIT).all())`. That is one `SELECT … ORDER BY … LIMIT 500` with no `COUNT` query. A
    `Page` would add one that the port never needs. The specification is built in private methods
    of the adapter, so no extra class is needed.
  - `findByStatusIn`: an empty collection returns `List.of()` without a query, as today.
- Mappers: `AnalysisToAnalysisEntityMapper` (with `AnalysisSpecToAnalysisSpecEmbeddableMapper`,
  `RunStatsToRunStatsEmbeddableMapper`), `AnalysisEntityToAnalysisMapper` (with
  `AnalysisSpecEmbeddableToAnalysisSpecMapper`, `RunStatsEmbeddableToRunStatsMapper`). Keep
  `MillisToDurationMapper`. Use `library.mapper.DurationToMillisMapper`. Delete `AnalysisRow`, the
  four row mappers, the `OffsetDateTime` converters, the local `DurationToMillisMapper`,
  `StringToAnalysisIdMapper` and the two analyst list mappers.

### identity

- `UserEntity` (`"USERS"`): `@EmbeddedId UserIdEmbeddable id`. `username` (`Username`,
  `UsernameAttributeConverter`) and `passwordHash` (`PasswordHash`, `PasswordHashAttributeConverter`).
  `CREATED_AT` has `@Column(updatable = false)`, so a re-save keeps the first creation time.
  `roleIds` is an `@ElementCollection Set<RoleId>` on `"USER_ROLES"` (`RoleIdAttributeConverter`).
- `RoleEntity` (`"ROLES"`): `@EmbeddedId RoleIdEmbeddable id`. `permissions` is a `Set<Permission>`
  on the `"PERMISSION"[]` array column `PERMISSIONS`.
- `UserJpaRepository extends JpaRepository<UserEntity, UserIdEmbeddable>`, `RoleJpaRepository extends
  JpaRepository<RoleEntity, RoleIdEmbeddable>`:
  - the user finders carry `@EntityGraph(attributePaths = "roleIds")`, so a read stays one query per
    call, like the batched `IN (…)` lookup today, instead of one per user. Role permissions come with
    the row (array) and need none.
  - `UserJpaRepository.findByUsernameIgnoringCase` is a JPQL
    `@Query("select u from UserEntity u where lower(u.username) = lower(:username)")`, which matches
    the `USERS_USERNAME_LOWER_UK` index. A derived `IgnoreCase` method would use `upper(…)` and miss
    the index. The parameter is the plain `String` (`username.value()`). If Hibernate types it
    through the converter, pass the `Username` instead. The test decides.
  - `findAllOrderedByUsername` is JPQL `… order by lower(u.username)`.
  - `RoleJpaRepository.findByName(String)` and `findAllByOrderByNameAsc()` are derived.
- `JpaUserRepositoryAdapter` / `JpaRoleRepositoryAdapter`:
  - `save` is `@Transactional` and calls `repository.saveAndFlush(entity)`: an upsert (`merge`) that
    replaces the role assignments (element collection) or the permissions (array) in the same
    transaction. The flush raises a unique or foreign-key
    violation inside `save`, as today.
  - Spring's exception translation on the repository turns the duplicate username (SQLState 23505 on
    `USERS_USERNAME_LOWER_UK`) into `DuplicateKeyException`, which the port's Javadoc promises. If
    the translation yields only `DataIntegrityViolationException`, the user adapter catches it and
    rethrows `DuplicateKeyException` when the cause is the unique violation. The existing test
    decides.
  - A save with an unknown role ID fails with a `DataAccessException` and leaves nothing behind
    (rollback).
  - `count` is `repository.count()`. Read methods that map entities are
    `@Transactional(readOnly = true)`, so a lazy collection never escapes a closed session (with
    `open-in-view=false`).
- Mappers: `UserToUserEntityMapper`, `UserEntityToUserMapper`, `RoleToRoleEntityMapper`,
  `RoleEntityToRoleMapper`. The ID embeddables map by `value`. The other wrappers, the role IDs and
  the permissions are the same types on both sides. `lockedUntil` maps `Optional<Instant>` ↔ nullable
  `Instant` (rename the two `Optional` converters to the `Instant` types). Delete the four rows, the
  row mappers, `StringToPermissionMapper`, the `String` → wrapper scalar mappers and the
  `OffsetDateTime` converters.

### ArchitectureTest

New or changed rules (field names as in the package-structure document):

- `production_code_does_not_use_plain_sql`: no class under `BASE` depends on
  `org.springframework.jdbc..`, `java.sql..` or `javax.sql..`. The same package ban covers test code
  through Checkstyle's `IllegalImport` (`config/checkstyle/checkstyle.xml`), since ArchUnit does not
  import tests. No production class configures
  Flyway, so the issue's "Flyway config aside" needs no exception. If the build shows one is
  needed, add it explicitly, with the reason.
- `no_native_queries`: no method annotated with `@Query` where `nativeQuery = true`, or with
  `@NativeQuery`. No call to `EntityManager.createNativeQuery`, `createNativeMutationQuery`
  (Hibernate `Session`) or `createStoredProcedureQuery`. A custom `ArchCondition` reads the
  annotation attribute.
- `entities_live_in_entity_packages`: classes annotated with `@Entity`, `@Embeddable`,
  `@MappedSuperclass` or `@Converter`, or implementing `AttributeConverter`, reside in
  `domain.*.adapter.*.entity`.
- `entity_packages_hold_only_entities`: top-level classes in `..entity..` are one of those.
- `spring_data_repositories_live_in_persistence_roots`: classes assignable to
  `org.springframework.data.repository.Repository` reside in `domain.*.adapter.persistence` (the port
  package root).
- `persistence_classes_are_named_by_kind`: `@Entity` → `*Entity`, `@Embeddable` → `*Embeddable`,
  `AttributeConverter` implementations → `*AttributeConverter`, Spring Data repositories →
  `*JpaRepository`. The reverse holds too: a class named `*Entity` is an `@Entity`.
- `libraries_depend_only_on_libraries`: classes in `….library..` depend on no project package
  outside `….library..`. This is what keeps the libraries technical: a library cannot even
  name a domain type, a port or a BFF class.
- `module_root_packages_hold_no_classes`: add `….library`, which is only a parent. A library's own
  root (`….library.mapper`) holds its classes directly, since the library is one kind of class.
- `library_mapper_holds_only_mappers`: every class in `….library.mapper..` is a MapStruct mapper (or
  its generated `*Impl`). A later library adds its own placement rule.
- `mappers_are_not_duplicated_across_modules`: no two `@Mapper` classes in different modules
  (`bff.delegate.impl`, `domain.<d>.adapter`, `library.mapper`) share a simple name. There is no
  exception after this change. The rule catches the next copy-pasted converter.
- `enums_are_not_stored_as_text`: an entity or embeddable field whose type is an enum, or a
  collection of an enum, carries `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` or
  `@JdbcTypeCode(SqlTypes.ARRAY)`, never a plain `@Enumerated` or a converter.
- `mappers_live_at_layer_boundaries`: also allow `….library.mapper..`.
- `domain_core_does_not_depend_on_infrastructure`: add `jakarta.persistence..` and `org.hibernate..`.
- Remove `rows_live_in_row_packages` and `row_packages_hold_only_row_records`.
  `adapter_records_live_in_data_packages` drops `row` (`json`, `spec`).
  `adapter_sub_packages_are_known_kinds` swaps `row` for `entity`.
  `port_package_roots_hold_only_adapters` also allows Spring Data repository interfaces.

## Work packages

### WP1: Build and configuration

- **Depends on**: none
- **Files**: `gradle/libs.versions.toml`, `artifact/backend/build.gradle.kts`,
  `artifact/backend/domain/{settings,analysis,identity}/adapter/build.gradle.kts`,
  `artifact/backend/src/main/resources/application.properties`, `config/checkstyle/checkstyle.xml`
  (`IllegalImport`), `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Catalog: add `spring-boot-starter-data-jpa`, `spring-data-jpa`, `jakarta-persistence-api`,
        `hibernate-core`; remove `spring-boot-starter-jdbc` and `spring-jdbc`
  - [ ] `:backend` on `spring-boot-starter-data-jpa`; the adapters on `spring-data-jpa` +
        `jakarta-persistence-api` + `hibernate-core`
  - [ ] The JPA properties block in `application.properties`, each line with a short comment
  - [ ] `IllegalImport` bans `org.springframework.jdbc` in main and test code
- **Tests**: `TradingPlatformApplicationTests` and `AnalysesApiIntegrationTest` start the whole app
  on PostgreSQL with `ddl-auto=validate`: every entity matches its migration.

### WP1b: `:backend:library:mapper` and the shared mappers

- **Depends on**: none (parallel to WP1)
- **Files**: `settings.gradle.kts` (`:backend:library:mapper`),
  `artifact/backend/library/mapper/build.gradle.kts`,
  `artifact/backend/library/mapper/src/main/java/…/library/mapper/package-info.java`,
  `…/library/mapper/DurationToMillisMapper.java`, `…/library/mapper/EnumToLowerCaseNameMapper.java`,
  tests under `artifact/backend/library/mapper/src/test/java/…/library/mapper/`;
  `artifact/backend/build.gradle.kts` (`runtimeOnly(project(":backend:library:mapper"))`),
  `bff/impl/build.gradle.kts` and `domain/analysis/adapter/build.gradle.kts` (`implementation`);
  the BFF and eventstore mappers that used the deleted copies; deleted:
  `bff/.../mapper/DurationToMillisMapper`, `bff/.../mapper/RunEventTypeToStringMapper`,
  `domain/analysis/adapter/.../eventstore/mapper/RunEventTypeToStringMapper` (the analysis
  persistence `DurationToMillisMapper` goes in WP3)
- **Steps**:
  - [ ] Module with the MapStruct plugin, no project dependency
  - [ ] The two mappers; event-type mappings switched to `qualifiedByName = "lowerCaseName"`
  - [ ] The analysis adapter tests' `@ComponentScan` include `library.mapper`
- **Tests**: `DurationToMillisMapperTest`, `EnumToLowerCaseNameMapperTest` (a multi-word constant
  such as `AGENT_STATUS` → `agent_status`). The existing BFF and eventstore tests prove the wire
  names did not change.

### WP1c: `:backend:library:persistence` with its test fixtures

- **Depends on**: WP1 (the JPA dependencies)
- **Files** (`TestMigrations.migrate(dataSource, baselineVersion, extraLocations…)`):
  `settings.gradle.kts`, `artifact/backend/library/persistence/build.gradle.kts`
  (`java-library` + `java-test-fixtures`),
  `…/library/persistence/src/testFixtures/java/…/library/persistence/{PostgresTestDatabase,TestMigrations,JpaAdapterTest}.java`,
  `…/src/testFixtures/resources/jpa-test.properties`, `…/src/main/java/…/library/persistence/package-info.java`
  (a placeholder for the later shared main code, with its rule in the Javadoc); the
  `testImplementation(testFixtures(…))` lines in `artifact/backend/build.gradle.kts` and the three
  adapter modules; `ArchitectureTest`'s test-fixture `ImportOption`; deleted: the four
  `PostgresTestDatabase` copies (the `:backend` one here, the adapter ones in WP2–WP4);
  `:backend`'s `TestPlatformHome` switched to the shared class
- **Steps**:
  - [ ] The module, the fixtures, the properties file
  - [ ] `:backend` tests on the shared `PostgresTestDatabase`
- **Tests**: used by every repository test (WP2–WP4) and by the `:backend` Spring Boot tests, which
  prove it works. No tests of its own beyond that.

### WP2: settings (the first slice)

- **Depends on**: WP1, WP1c
- **Files**: `domain/settings/adapter/src/main/java/…/settings/adapter/persistence/`
  (`JpaPresetRepositoryAdapter`, `PresetJpaRepository`, `entity/PresetEntity`,
  `entity/PresetIdEmbeddable`,
  `mapper/PresetToPresetEntityMapper`, `mapper/PresetEntityToPresetMapper`); deleted:
  `JdbcPresetRepositoryAdapter`, `row/PresetRow`, `mapper/PresetRowToPresetMapper`,
  `mapper/PresetToPresetRowMapper`, `mapper/InstantToOffsetDateTimeMapper`,
  `mapper/OffsetDateTimeToInstantMapper`, `mapper/StringToPresetIdMapper`; tests:
  `JdbcPresetRepositoryAdapterTest` →
  `JpaPresetRepositoryAdapterTest`, `mapper/TimestampMappersTest` deleted
- **Steps**:
  - [ ] Entity, repository, mappers, adapter as in Design
  - [ ] The test on the JPA setup (see Tests), the same assertions as today
- **Tests**: `JpaPresetRepositoryAdapterTest.savesUpdatesAndDeletes` (upsert, read back, delete
  reports `true` then `false`).

### WP3: analysis

- **Depends on**: WP1b, WP2 (the shared mappers, the pattern and the test setup)
- **Files**: `domain/analysis/adapter/src/main/resources/db/migration/V4__analysis_enum_types.sql`;
  `domain/analysis/adapter/src/main/java/…/analysis/adapter/persistence/`
  (`JpaAnalysisRepositoryAdapter`, `AnalysisJpaRepository`, `entity/AnalysisEntity`,
  `entity/AnalysisIdEmbeddable`, `entity/AnalysisSpecEmbeddable`, `entity/RunStatsEmbeddable`, the
  new mappers); deleted: `JdbcAnalysisRepositoryAdapter`, `row/AnalysisRow`, the four
  `AnalysisRow*` mappers, the `OffsetDateTime` converters, `StringToAnalysisIdMapper`, the analyst
  list mappers; tests: `AdapterTestSupport` (JPA instead of `JdbcClient` wiring),
  `JdbcAnalysisRepositoryAdapterTest` → `JpaAnalysisRepositoryAdapterTest`, new
  `V4AnalysisEnumTypesMigrationTest`, `mapper/TimestampMappersTest` deleted
- **Steps**:
  - [ ] The migration
  - [ ] Entity with the ID and the two embeddables, the enum and array columns, `Persistable`;
        repository; mappers; adapter with `applyRunState` / `replaceImported` and the
        `Specification` + limit query
  - [ ] `AdapterTestSupport` switched to the JPA setup; the other analysis adapter tests that extend
        it (runner, eventstore, eventline) still pass
- **Tests**: every existing case of `JdbcAnalysisRepositoryAdapterTest`, unchanged in substance:
  round trip through every transition, native types (microsecond timestamps; the `DATE` and enum
  column types are now checked by `ddl-auto=validate` instead of the raw `JdbcClient` query),
  newest first and filters, `replaceImported` only on EXTERNAL records, `update` of a missing
  analysis fails, unknown IDs are empty. New: `listIsLimitedToFiveHundredNewest` (501 rows, the
  oldest one missing), `updateKeepsTheSpecAndCreationTime` (an `update` with a changed spec leaves
  the stored spec), `insertingAnExistingIdFails`, `everyEnumConstantRoundTrips` (every
  `AnalysisStatus`, `AnalysisSource`, `AssetType`, `Rating` and `Analyst` value saved and read back,
  so a Java constant without a database label fails here), `noAnalystsRoundTripsAsAnEmptyArray`.
  `V4AnalysisEnumTypesMigrationTest`: migrates with the seed location, whose `V1_1` migration inserts
  rows in the old text form (including `'NEWS, MARKET'` with a space, an empty `ANALYSTS`, a null
  `RATING`), migrates to V4, and the test reads them back through the repository, unchanged.

### WP4: identity

- **Depends on**: WP2
- **Files**: `domain/identity/adapter/src/main/resources/db/migration/V5__identity_permission_array.sql`;
  `domain/identity/adapter/src/main/java/…/identity/adapter/persistence/`
  (`JpaUserRepositoryAdapter`, `JpaRoleRepositoryAdapter`, `UserJpaRepository`,
  `RoleJpaRepository`, `entity/UserEntity`, `entity/RoleEntity`, `entity/UserIdEmbeddable`,
  `entity/RoleIdEmbeddable`, `entity/UsernameAttributeConverter`,
  `entity/PasswordHashAttributeConverter`, `entity/RoleIdAttributeConverter`, the new mappers);
  deleted: both `Jdbc*` adapters, `row/*`, the row mappers, `StringToPermissionMapper`, the
  `String` → wrapper mappers, the `OffsetDateTime` converters; tests: `JdbcIdentityRepositoryTest`
  → `JpaIdentityRepositoryTest`, `mapper/TimestampMappersTest` deleted, new
  `entity/*AttributeConverterTest` and `V5IdentityPermissionArrayMigrationTest`
- **Steps**:
  - [ ] The migration
  - [ ] Entities, embeddables, converters, repositories with the entity graph and the JPQL username
        queries, mappers, adapters
  - [ ] Duplicate-username translation verified by the existing test (adapter translation only if
        needed)
- **Tests**: every existing case of `JdbcIdentityRepositoryTest`, without `JdbcClient`:
  - round trips, update replaces fields and roles, creation time kept, case-insensitive lookup,
    duplicate username → `DuplicateKeyException`, count/findAll, role permissions replaced,
    findByName.
  - "an assigned role cannot be deleted" deletes through `RoleJpaRepository` (same package) and
    expects a `DataIntegrityViolationException`.
  - "deleting a user keeps the role" deletes through `UserJpaRepository`.
  - "a failing save leaves no partial assignment" reads the user back through the port, which finds
    nothing.

  New:
  - `everyPermissionRoundTrips` (a role with all `Permission` values).
  - the three converter tests (round trip; `null` stays `null`).
  - `V5IdentityPermissionArrayMigrationTest`: the seed migration `V3_1` inserts roles with
    `ROLE_PERMISSIONS` rows for every key before V5 runs. The test reads the roles back with every
    permission, and checks that a role with no permissions gets `{}`.

### WP6: Testcontainers on a Podman machine (added during implementation, approved by the developer)

- **Depends on**: WP1c (the shared `PostgresTestDatabase`)
- **Why**: the repository tests did not start on the developer's Podman machine (macOS). Ryuk,
  Testcontainers' cleanup container, mounts the `DOCKER_HOST` socket path. That is the host's path
  and does not exist inside the machine's VM ("read-only file system"). The agent had worked around
  it with an untracked `mise.local.toml` that switched Ryuk off. The developer asked for a fix
  committed in the right place, one that works for every developer, Docker and Podman alike.
- **Files**: `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`;
  `README.md` (the tasks paragraph); `docs/coding-convention/backend-java-persistence.md` (Tests)
- **Steps**:
  - [x] Every Gradle `Test` task: when `DOCKER_HOST` points at a Podman machine and neither
        `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` nor `TESTCONTAINERS_RYUK_DISABLED` is set, a rootful
        machine gets `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/run/podman/podman.sock`, the socket
        inside the VM, so Ryuk keeps cleaning up. A rootless machine gets
        `TESTCONTAINERS_RYUK_DISABLED=true`, since its socket path depends on its user. Docker, and
        Podman on Linux without a machine, are unchanged. A value set in the environment wins.
- **Tests**: the repository tests on a rootful Podman machine with Ryuk running, and CI (Docker)
  unchanged.

### WP5: Convention, docs and ArchUnit rules

- **Depends on**: WP1b, WP2–WP4 (the rules must pass on the migrated code)
- **Files**: `docs/coding-convention/backend-java-persistence.md` (new),
  `docs/coding-convention/README.md`, `docs/coding-convention/backend-java-package-structure.md`,
  `docs/coding-convention/backend-database-naming.md`,
  `artifact/backend/src/test/java/…/ArchitectureTest.java`, the three version files
- **Steps**:
  - [ ] The rules in `ArchitectureTest` as in Design
  - [ ] Each new or changed rule shown failing on a deliberately misplaced class or call (an
        `@Entity` outside `entity`, a `JdbcClient` field in an adapter, a `@Query(nativeQuery =
        true)`, a repository outside `persistence`, a `jakarta.persistence` import in a core, an `@Entity`
        without the `Entity` suffix, a copied mapper whose name clashes, a project import in
        `library`, an enum field mapped with plain `@Enumerated`, an `org.springframework.jdbc`
        import in a test for Checkstyle), then
        reverted. List what was tried in the pull request description.
  - [ ] The documents (see Docs to update)
  - [ ] `mise run version:bump major`
- **Tests**: `ArchitectureTest` green on the migrated code; `mise run check`; `mise run build`.

## Tests

- **Test setup for the adapter modules**: `@JpaAdapterTest` from the test fixtures of
  `:backend:library:persistence` (WP1c) on the test class, or on `AdapterTestSupport` for analysis. It imports Boot's Hibernate JPA,
  Spring Data JPA repositories and transaction auto-configurations and loads
  `jpa-test.properties` (`ddl-auto=validate`, naming strategy, global quoting). The module's
  own `Config` builds the data source from the shared `PostgresTestDatabase` (Testcontainers, one
  database per context), runs `TestMigrations.migrate(dataSource, "<baseline>")` so only the
  module's own migration applies, and carries `@AutoConfigurationPackage`, so entity and repository
  scan stay in the module, and `@ComponentScan` of its adapter and mappers (plus `library.mapper`
  where used). Each module's test therefore also validates its entities against its migration.
  `@DataJpaTest` is not used: its per-test transaction and test-database replacement would both
  have to be switched off, and it would not cover `AdapterTestSupport`, which wires all analysis
  adapters. In short:
- **No test-managed transaction.** The tests must not run inside one transaction (`@JpaAdapterTest`
  adds none, and no test is `@Transactional`).
  Every port call runs in its own transaction as in production, and a read after a write goes to the
  database, not to the first-level cache. Otherwise "creation time kept" or "duplicate username
  fails" could pass without the database ever seeing the statement.
- **No `JdbcClient` in tests either.**
  - Raw checks become repository calls: deletes through the package-private Spring Data
    repositories, reads through the ports.
  - Column types are covered by `ddl-auto=validate`.
  - The migration tests seed old-form rows with Flyway itself: a test-only migration in
    `src/test/resources/db/seed/<domain>` (`V1_1__analysis_seed_old_rows.sql`,
    `V3_1__identity_seed_old_rows.sql`) between the real versions. `TestMigrations` takes extra
    locations for it. Plain SQL in a migration file is not plain SQL in code.
  - Checkstyle's `IllegalImport` enforces this.
- **Migration tests** (`V4…`, `V5…`): an old-form database converted by the new migration, read
  back unchanged. They prove "existing databases work without data loss".
- `artifact/backend` Spring Boot tests (`TradingPlatformApplicationTests`,
  `AnalysesApiIntegrationTest`) prove the app starts on PostgreSQL with `ddl-auto=validate` and all
  three domains' entities, and that the API behaves as before. e2e (`mise run e2e`) runs unchanged.
- "Done when" (issue): no `JdbcClient`/`JdbcTemplate`/native query in production or test code
  (`production_code_does_not_use_plain_sql`, `no_native_queries`); app starts with `validate` (the
  Spring Boot tests); every repository contract test passes (WP2–WP4); no API/UI change (e2e and API
  integration test unchanged); convention documented (WP5).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-persistence.md` (new) | Persistence through Spring Data JPA / Hibernate only. Forbidden in production code: `JdbcClient`, `JdbcTemplate`, `NamedParameterJdbcTemplate`, `DataSource`/`Connection`, native queries (`@Query(nativeQuery = true)`, `@NativeQuery`, `createNativeQuery`), with the reasons: one mapping, schema validated at startup, no SQL strings that drift from the migrations, queries typed against entities. Where entities, embeddables, converters and repositories live; the core never imports `jakarta.persistence` / `org.hibernate`. When to use `@Embeddable` (columns that form a domain value; every ID as `@EmbeddedId`), an `AttributeConverter` (every other single-value wrapper, never on an ID), a PostgreSQL enum type (every enum column, labels = constant names, no enum as text), a PostgreSQL array (a set of values without a foreign key), an element collection on a join table (references to another aggregate, so the foreign key stays; no `@ManyToMany` between aggregates). No `spring-jdbc` in production or test code, with the reason it is still on the classpath. Upsert via `save`/`merge`, `Persistable` for insert-only entities, `saveAndFlush` where a constraint error must surface in the call, `@EntityGraph` against N+1, read methods `@Transactional(readOnly = true)`. Flyway owns the schema, `ddl-auto=validate`, the naming strategy and global quoting. Where each rule is checked (the ArchUnit rule names). Tests: `@JpaAdapterTest` from the test fixtures of `:backend:library:persistence`, why test fixtures, no test-managed transaction and why. What goes into `:backend:library:persistence`'s main source set once a second adapter needs it, and why there is no `@MappedSuperclass`. |
| Text | `docs/coding-convention/README.md` | Index row for the new document. Enforcement table: the database naming row's test names (`Jpa*` instead of `Jdbc*`). An ArchUnit mention of the persistence rules if the table lists rule groups. |
| Text | `docs/coding-convention/backend-java-package-structure.md` | Package layout: `entity/` replaces `row/`, repositories at the port root. Adapter table: `Jpa*RepositoryAdapter` and `entity` for analysis, identity and settings. "Where each class kind goes": the entity, embeddable, converter, repository and native-query rows (with their suffixes) replace the two row rows; records now `json` or `spec`; known sub-packages. Visibility: entities public, repositories package-private. Modules table: `:backend:library:<library>` as the place for shared libraries, with `:backend:library:mapper` (`….library.mapper`, generic shared mappers) and `:backend:library:persistence` (test fixtures now, shared persistence base types later); shared test code lives in a module's test fixtures (`java-test-fixtures`), never copied and never in a test-only main module; package layout gets `library/<library>/`. "Shared converters and accepted duplicates" rewritten: generic converters that two modules need live in `:backend:library:mapper`; how a new shared library is added (module, Modules row, placement rule, depends only on other libraries); the alternatives and why modules were chosen; the accepted-duplicates table keeps only `DockerClients`/`RunnerKind` and `Rating`, each with its reason; the new rules named. |
| Text | `docs/coding-convention/backend-java-persistence.md` | Also the naming table (`*Entity`, `*Embeddable`, `*AttributeConverter`, `*JpaRepository`, `Jpa*RepositoryAdapter`) and why the suffix. |
| Text | `docs/coding-convention/backend-database-naming.md` | Enum types are named like tables (`"ANALYSIS_STATUS"`, quoted, upper snake case of the Java enum), their labels are the Java constant names; array columns are `"<TYPE>"[]`; the V4/V5 examples. Names are quoted in the migrations and in the entity mappings (`@Table(name = "ANALYSES")`, `@Column(name = "CREATED_AT")`), reaching the database quoted through `globally_quoted_identifiers` and the standard physical naming strategy. Replace the `Jdbc*Adapter` SQL examples with an entity mapping example. Remove the "Named parameters" and "Row records" bullets. The "Where it is checked" row: `ddl-auto=validate` now also catches a name that does not match. Tests' SQL keeps quoting. |
| Text | Root `README.md` | The upgrade note next to the #113 one: this major release migrates the database in place (enum types, permissions array), so an older release cannot run on it afterwards; take a `pg_dump` first. The CI table already says "every repository and Spring Boot test against PostgreSQL". |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | `IllegalImport` now also bans `org.springframework.jdbc`, and why. |
| Screenshot | none | No page changes. |

## Out of scope

- An automated check of the stored names in the catalog: #115.
- Optimistic locking (`@Version`): #116.
- Auditing (`@CreatedDate` / `@LastModifiedDate`): #117.
- Other schema changes beyond the enum types and the arrays.
- New queries or ports. Only what the existing ports do is migrated.

## Open questions

- Settled in this plan, confirm before approving:
  1. Spring Data repositories are package-private at the port package root, next to their adapter,
     and `port_package_roots_hold_only_adapters` is widened for them. The alternative is a public
     `repository` sub-package.
  2. One pull request for the whole issue (about 2500 changed lines with the two shared libraries
     and the two migrations, many of them deletions), rather than a split into settings + analysis
     and identity + rules. A split would leave `row` and `entity` side by side, and the convention
     half-written, between the two.
