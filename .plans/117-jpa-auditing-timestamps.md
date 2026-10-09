# Plan: Auditing timestamps with Spring Data JPA auditing

- **Issue**: #117 (Persistence: auditing timestamps with Spring Data JPA auditing)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Technical audit timestamps are set by Spring Data JPA auditing, from one application `Clock`, instead
of by each caller. `USERS.CREATED_AT`, `USERS.UPDATED_AT` and `PRESETS.UPDATED_AT` become audit
fields. `ANALYSES.CREATED_AT` stays a domain fact set by the analysis core, now from the same `Clock`
instead of `Instant.now()`, so it is testable without a database and with a fixed time. The rule
for choosing between the two is written into the persistence convention. The REST API and the
values users see do not change.

The issue's "current state" says the cores set the timestamps "from their `Clock`". The code does
not: `PresetService` and `AnalysisService` call `Instant.now()`, and there is no `Clock` bean.
Only `ImportExistingRunsService` (orchestration) builds its own `Clock.systemUTC()`. This plan adds
the `Clock` bean.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend | `backend-java-persistence.md`: the core has no persistence dependency (no `org.springframework.data` in a core); entities in `adapter.persistence.entity`; MapStruct at the adapter boundary; partial updates load the managed entity in a `@Transactional` adapter method; `saveAndFlush` inside `@Transactional` for constraint errors; repository tests on PostgreSQL through the port, no test-managed transaction; `@JpaAdapterTest` wiring. `backend-java-package-structure.md`: `:backend:library:persistence` holds technical code only, its classes directly in its root package, depending on libraries only; `:backend` assembles the modules. `backend-java-checkstyle.md` / `backend-java-formatting.md`: Spotless, Checkstyle, suppressions in place with a reason. |
| docs | `docs/coding-convention/README.md`: decisions and their reasons go into the coding-convention document of the part. |

## Design

### Which timestamp is what

| Column | Kind | Why |
|---|---|---|
| `ANALYSES.CREATED_AT` | Domain fact | An imported run takes the start time of the original run (`Analysis.fromExternal`), not the time of the insert. Recovery orders queued analyses by it (`AnalysisService#afterSingletonsInstantiated`). It stays in the core and comes from the `Clock`. |
| `USERS.CREATED_AT` | Audit field (`@CreatedDate`) | When the row was inserted. No domain rule reads it. |
| `USERS.UPDATED_AT` | Audit field (`@LastModifiedDate`) | When the row was last written. No domain rule reads it. |
| `PRESETS.UPDATED_AT` | Audit field (`@LastModifiedDate`) | When the preset was last saved. It is shown to the user, but it only records the write, and no rule depends on it. |

The rule for the convention: **a timestamp is a domain fact when the domain gives it its value or
decides with it** (it can differ from the time of the write, or a rule or ordering in the core uses
it). Then the core sets it from the injected `Clock`. **Otherwise it is an audit field**: it records
when the row was written, and auditing sets it. `@CreatedBy` / `@LastModifiedBy` are not added (see
"Out of scope").

### Wiring

- **`Clock` bean**: `TradingPlatformApplication` gets a `@Bean Clock clock()` returning
  `Clock.systemUTC()`. `:backend` is the module that assembles the others, so the one
  application-wide `Clock` is defined there. Cores and adapters inject `java.time.Clock`, which is
  not a persistence or Spring type, so the core rule is kept.
- **`:backend:library:persistence` main** gets its first classes, in its root package
  `….library.persistence`:
  - `ClockDateTimeProvider implements DateTimeProvider`: `getNow()` returns
    `Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS))`. PostgreSQL's `TIMESTAMPTZ` keeps
    microseconds, so the value returned from `save` equals the one read back later.
  - `JpaAuditingConfiguration`: a `@Configuration` with
    `@EnableJpaAuditing(dateTimeProviderRef = "clockDateTimeProvider")`, defining the
    `ClockDateTimeProvider` bean from the `Clock` bean. The application finds it by component scan
    (it is under the base package of `TradingPlatformApplication`).
  - Build: `api(libs.spring.data.jpa)` and `implementation(libs.spring.context)` in
    `library/persistence/build.gradle.kts`. The settings and identity adapters add
    `implementation(project(":backend:library:persistence"))`, and `:backend` adds
    `runtimeOnly(project(":backend:library:persistence"))` next to `library:mapper`.
  - Update the `package-info.java` text, which says "Nothing here yet".
- **Test fixtures**: `@JpaAdapterTest` also imports `JpaAuditingConfiguration` and a new
  `TestClockConfiguration` that provides a `MutableTestClock` (a `Clock` starting at a fixed instant,
  with `set(Instant)` and `advance(Duration)`) as the `Clock` bean. Every adapter test is then wired
  as in the application, with a fixed time it controls. The analysis adapter test gets the bean too
  and ignores it.

### Entities and adapters

- `PresetEntity` and `UserEntity` get `@EntityListeners(AuditingEntityListener.class)`.
  `PresetEntity.updatedAt` gets `@LastModifiedDate`. `UserEntity.createdAt` gets `@CreatedDate` and
  keeps `updatable = false` as a guard. `UserEntity.updatedAt` gets `@LastModifiedDate`.
- **The domain records keep the audited fields for reading.** `Preset.updatedAt`,
  `User.createdAt` and `User.updatedAt` stay on the records, since the API shows the preset's time.
  They are `null` on a record the core builds before its first save, and set on every record the
  port returns. Remove their `requireNonNull` and document this on the record (Javadoc on the
  components).
- **The adapters never write an audited field from the domain.** The `PresetToPresetEntityMapper`
  and `UserToUserEntityMapper` ignore them (`@Mapping(target = "…", ignore = true)`).
- **`save` returns the stored record.** `PresetRepositoryPort.save` and `UserRepositoryPort.save`
  return `Preset` / `User` with the audited values, mapped from the entity after the flush.
  - `JpaPresetRepositoryAdapter#save`: `@Transactional`, `saveAndFlush` (merge), and map the returned
    managed entity. The incoming entity carries `updatedAt == null`, so the merge always makes the
    row dirty, and every save sets `UPDATED_AT`, also when name and payload are unchanged. That is
    today's behaviour. Say so in a comment.
  - `JpaUserRepositoryAdapter#save`: a merge would copy the incoming `createdAt == null` onto the
    managed entity, and the returned user would have no creation time. So it follows the
    partial-update pattern: load the managed entity by ID. If it exists, copy the domain fields and
    the role IDs onto it, and clear `updatedAt` so the flush always updates the row and auditing
    sets the time (comment why). If it does not exist, persist the new entity. Then `flush` and map
    the managed entity. The `DuplicateKeyException` translation stays as it is.
- **Cores**:
  - `PresetService` no longer creates the time: it builds the `Preset` with `updatedAt` `null` and
    returns what `repository.save` returns, so the API keeps returning the stored time.
  - `AnalysisService` injects `Clock` and uses `clock.instant()` for `Analysis.queued`, `running`,
    `finished` and the `RUN_FINISHED` event, instead of `Instant.now()`. Check the rest of the
    analysis core for `Instant.now()` and replace it the same way.
- **ArchUnit**: a new rule in `PersistenceArchitectureTest`,
  `audited_entities_have_the_auditing_listener`: a class with a field annotated `@CreatedDate`,
  `@LastModifiedDate`, `@CreatedBy` or `@LastModifiedBy` is annotated
  `@EntityListeners(AuditingEntityListener.class)`. Without it, the annotations are silently
  ignored, and a `NOT NULL` column fails only on insert.

## Work packages

### WP1: Clock and auditing wiring

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/library/persistence/build.gradle.kts`
  - `artifact/backend/library/persistence/src/main/java/…/library/persistence/ClockDateTimeProvider.java` (new)
  - `artifact/backend/library/persistence/src/main/java/…/library/persistence/JpaAuditingConfiguration.java` (new)
  - `artifact/backend/library/persistence/src/main/java/…/library/persistence/package-info.java`
  - `artifact/backend/library/persistence/src/testFixtures/java/…/library/persistence/JpaAdapterTest.java`
  - `artifact/backend/library/persistence/src/testFixtures/java/…/library/persistence/MutableTestClock.java` (new)
  - `artifact/backend/library/persistence/src/testFixtures/java/…/library/persistence/TestClockConfiguration.java` (new)
  - `artifact/backend/library/persistence/src/test/java/…/library/persistence/ClockDateTimeProviderTest.java` (new)
  - `artifact/backend/src/main/java/…/TradingPlatformApplication.java`
  - `artifact/backend/build.gradle.kts`
- **Steps**:
  - [x] Add the `Clock` bean to `TradingPlatformApplication`.
  - [x] Add `ClockDateTimeProvider` and `JpaAuditingConfiguration` with their dependencies.
  - [x] Add `MutableTestClock` and `TestClockConfiguration` to the test fixtures, and import them
        and `JpaAuditingConfiguration` in `@JpaAdapterTest`.
  - [x] Put `:backend:library:persistence` on the application's runtime classpath.
- **Tests**: `ClockDateTimeProviderTest` (unit): returns the clock's instant truncated to
  microseconds. `TradingPlatformApplicationTests` still starts the context (one `Clock`, auditing
  enabled).

### WP2: Presets audited

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/domain/settings/core/src/main/java/…/settings/core/model/Preset.java`
  - `artifact/backend/domain/settings/core/src/main/java/…/settings/core/outbound/persistence/PresetRepositoryPort.java`
  - `artifact/backend/domain/settings/core/src/main/java/…/settings/core/service/PresetService.java`
  - `artifact/backend/domain/settings/core/src/test/java/…/settings/core/service/PresetServiceTest.java` (new)
  - `artifact/backend/domain/settings/adapter/build.gradle.kts`
  - `artifact/backend/domain/settings/adapter/src/main/java/…/settings/adapter/persistence/entity/PresetEntity.java`
  - `artifact/backend/domain/settings/adapter/src/main/java/…/settings/adapter/persistence/mapper/PresetToPresetEntityMapper.java`
  - `artifact/backend/domain/settings/adapter/src/main/java/…/settings/adapter/persistence/JpaPresetRepositoryAdapter.java`
  - `artifact/backend/domain/settings/adapter/src/test/java/…/settings/adapter/persistence/JpaPresetRepositoryAdapterTest.java`
- **Steps**:
  - [x] Make `Preset.updatedAt` nullable before the first save, and document it.
  - [x] Make `PresetRepositoryPort.save` return the stored `Preset`.
  - [x] Add the auditing annotations to `PresetEntity`, ignore `updatedAt` in the entity mapper, and
        change the adapter's `save` as in the design.
  - [x] Change `PresetService` to return the stored preset, with no `Instant.now()`.
- **Tests**:
  - `JpaPresetRepositoryAdapterTest`, with `MutableTestClock`: a new preset gets the clock's time,
    in the returned record and on read. An update after `advance` gets the new time. An update with
    unchanged name and payload still moves `updatedAt`. The existing round-trip tests are adapted to
    the audited field.
  - `PresetServiceTest` (unit, fake port): create and update return the port's stored preset, with
    a `null` `updatedAt` handed to the port.
  - The BFF's preset API tests, if any, still pass unchanged (no API change).

### WP3: Users audited

- **Depends on**: WP1
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/…/identity/core/model/User.java`
  - `artifact/backend/domain/identity/core/src/main/java/…/identity/core/outbound/persistence/UserRepositoryPort.java`
  - `artifact/backend/domain/identity/core/src/test/java/…/identity/core/model/UserTest.java`
  - `artifact/backend/domain/identity/adapter/build.gradle.kts`
  - `artifact/backend/domain/identity/adapter/src/main/java/…/identity/adapter/persistence/entity/UserEntity.java`
  - `artifact/backend/domain/identity/adapter/src/main/java/…/identity/adapter/persistence/mapper/UserToUserEntityMapper.java`
  - `artifact/backend/domain/identity/adapter/src/main/java/…/identity/adapter/persistence/JpaUserRepositoryAdapter.java`
  - `artifact/backend/domain/identity/adapter/src/test/java/…/identity/adapter/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [ ] Make `User.createdAt` / `updatedAt` nullable before the first save, and document it.
        `withRoleIds` keeps them as they are.
  - [ ] Make `UserRepositoryPort.save` return the stored `User`.
  - [ ] Add the auditing annotations to `UserEntity`, ignore both fields in the entity mapper, and
        change the adapter's `save` to the load-and-copy form of the design.
- **Tests** (`JpaIdentityRepositoryTest`, with `MutableTestClock`):
  - A new user gets `createdAt == updatedAt ==` the clock's time, in the returned record and on read.
  - After `advance`, a re-save keeps `createdAt` and moves `updatedAt`. This replaces
    `updatingAUserKeepsTheCreationTimeOfTheFirstSave`, which passed a different `createdAt`.
  - A re-save that only changes the role assignments moves `updatedAt` too.
  - The round-trip tests compare everything except the audited fields with what was saved, and
    the audited fields with the clock.
  - A taken username still fails with `DuplicateKeyException`, on insert and on update.

### WP4: Analysis core on the Clock

- **Depends on**: WP1 (the `Clock` bean)
- **Files**:
  - `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/service/AnalysisService.java`
  - `artifact/backend/domain/analysis/core/src/test/java/…/analysis/core/service/AnalysisServiceTest.java`
  - any other test that constructs `AnalysisService` (`grep -rn "new AnalysisService"`)
- **Steps**:
  - [ ] Inject `Clock` into `AnalysisService` and replace every `Instant.now()` in the analysis core
        with `clock.instant()`.
- **Tests**: `AnalysisServiceTest` builds the service with `Clock.fixed(…)` and asserts that
  `createdAt` of a started analysis, `startedAt` / `endedAt` and the `RUN_FINISHED` event time are
  the clock's time.

### WP5: Architecture rule and docs

- **Depends on**: WP2, WP3
- **Files**:
  - `artifact/backend/src/test/java/…/PersistenceArchitectureTest.java`
  - `docs/coding-convention/backend-java-persistence.md`
  - `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [ ] Add `audited_entities_have_the_auditing_listener`.
  - [ ] Update the docs (see "Docs to update").
- **Tests**: the new ArchUnit rule passes on the code. Check that it fails when the listener is
  removed from `PresetEntity` (locally, not committed).

## Tests

- Unit: `ClockDateTimeProviderTest`, `PresetServiceTest`, `AnalysisServiceTest` with a fixed clock,
  `UserTest`.
- Repository (Testcontainers PostgreSQL, `@JpaAdapterTest` with `MutableTestClock`): every audited
  column, `USERS.CREATED_AT`, `USERS.UPDATED_AT` and `PRESETS.UPDATED_AT`, is asserted against the
  fixed clock on insert and on update. This is the issue's first acceptance criterion.
- Application: `TradingPlatformApplicationTests` and `AnalysesApiIntegrationTest` start the real
  context with auditing. ArchUnit: the new rule and the existing persistence rules.
- No API change: `openapi.json` and the frontend API types do not change (`mise run api-types`
  leaves no diff). The e2e tests that create presets still pass. This is the second acceptance
  criterion.
- `mise run check` passes.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-persistence.md`, new section "Timestamps: domain facts and audit fields" | The rule (domain fact → core with the injected `Clock`; otherwise audit field → `@CreatedDate` / `@LastModifiedDate`), the table of today's four columns, the `Clock` bean and `ClockDateTimeProvider` (microseconds), the records keeping audited fields for reading (`null` before the first save, `save` returns the stored record), the mappers ignoring them, and why the user adapter loads and copies instead of merging |
| Text | same doc, "Mapping domain types" | The timestamp row points to the new section |
| Text | same doc, "Repositories and adapters", "Upsert" bullet | `USERS.CREATED_AT` is kept by `@CreatedDate` with the load-and-copy save. `updatable = false` stays as a guard |
| Text | same doc, "Shared persistence code" | Main source set: `JpaAuditingConfiguration` and `ClockDateTimeProvider`, instead of "empty today". Test fixtures: `MutableTestClock`. Keep the "no `@MappedSuperclass` for timestamps" note |
| Text | same doc, "Tests" | `@JpaAdapterTest` brings auditing and the `MutableTestClock`. Audited columns are asserted against it |
| Text | same doc, "Where it is checked" | Row for `audited_entities_have_the_auditing_listener` |
| Text | `docs/coding-convention/backend-java-package-structure.md` | `:backend` row: `TradingPlatformApplication` and the application-wide `Clock` bean. Shared-libraries table: `:backend:library:persistence` holds the auditing configuration, "none yet" removed, and its users |
| Text | `README.md` | None: the README does not describe persistence internals, and nothing users see changes |
| Screenshot | none | No page changes |

## Out of scope

- `@CreatedBy` / `@LastModifiedBy`: they need authentication (#79 follow-ups) and new columns in a
  new migration per domain. They go into the issue that adds the login. The convention section names
  them as the way to do it.
- `ImportExistingRunsService` keeps its own `Clock.systemUTC()` default constructor. Moving it to
  the bean is a separate clean-up.
- No migration: the columns stay as they are, with no database default.
