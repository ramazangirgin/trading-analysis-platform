# Plan: Optimistic locking with @Version on the JPA entities

- **Issue**: #116 (Persistence: optimistic locking with @Version on the JPA entities)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): a migration
  adds a column with a default, and the REST API gains an optional field only

## Goal

A write based on a stale copy of an analysis, preset, user or role fails instead of silently
overwriting a newer one. Every updated-in-place table gets a `VERSION` column, every entity a
`@Version`, and the version travels with the domain record from the read to the write, so a stale
write is caught between two requests too, not only inside one transaction. A stale write surfaces
as a domain error code (`CONCURRENT_UPDATE`) and, where the API exposes the write, as HTTP 409. The
preset API carries the version, so renaming a preset that someone else changed meanwhile answers 409
and the Settings page shows why.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend: adapters (`domain/{analysis,settings,identity}/adapter`) | `backend-java-persistence.md`: entities in `adapter.persistence.entity`, MapStruct at the boundary, ports persistence-agnostic, no native SQL, repository tests on PostgreSQL through the port with `@JpaAdapterTest`, no test-managed transaction. `backend-database-naming.md`: uppercase quoted names, one migration per domain change, `V<n>__<domain>_<change>.sql`, never edit an applied migration |
| backend: cores (`domain/{analysis,settings,identity}/core`) | `backend-java-package-structure.md`: error codes in `core.exception` next to their exception; the core imports no `jakarta.persistence`, `org.hibernate`, `org.springframework.data` (`domain_core_does_not_depend_on_infrastructure`). `org.springframework.dao` is allowed, as `UserRepositoryPort` already promises `DuplicateKeyException` |
| backend: BFF (`bff/api`, `bff/impl`) | Exception → `ApiException` mappers in `bff.delegate.impl.mapper.error`; DTOs in `bff.controller.api.model` |
| backend: architecture tests (`artifact/backend/src/test`) | `PersistenceArchitectureTest`: one rule per persistence convention; `docs/coding-convention/README.md` "Changing a convention" (rule + doc + code together, show the rule fails on a broken case) |
| frontend (`artifact/frontend`) | `frontend-folder-structure.md`: the API call stays in `features/settings/api.ts`, types from `shared/api` (regenerated with `mise run api-types`), error codes translated in `shared/i18n/locales/*.json` |
| docs | `backend-java-persistence.md`, `docs/coding-convention/README.md`, root `README.md` |

## Design

### Where the version lives: on the domain record

The version travels through the core as a field of the aggregate record: `Long version` on
`Analysis`, `Preset`, `User` and `Role`. An adapter-only check (load, compare, save in one
transaction) would only protect the few milliseconds between the adapter's own read and its write;
the races the issue names (two admins editing one user, the runner updating while an import replaces)
are between a read in one call and a write in a later one. A plain `Long` keeps the core free of
persistence types.

- **Semantics**, written in each record's Javadoc like the audited fields: `null` on a record the
  core builds before its first save (a new aggregate); set on every record a port returns. It is
  owned by persistence: the core copies it along through its transitions (`Analysis.running`,
  `finished`, `withStats`, `withDecision`, `User.withRoleIds`, `Role.withPermissions`) and never
  changes it.
- **The ports promise** (Javadoc on each write method): a record whose `version` differs from the
  stored row's fails with Spring's `org.springframework.dao.OptimisticLockingFailureException` and
  leaves the row unchanged; a `null` version means "insert", so it fails on an existing ID as well.
  Spring's JPA exception translation already yields `ObjectOptimisticLockingFailureException` (a
  subclass) for Hibernate's `StaleObjectStateException` / `OptimisticLockException`.
- **Writes return the stored record**, with its new version, so a caller never keeps a record with an
  outdated version: `Preset save` and `User save` already do; `AnalysisRepositoryPort.update` and
  `replaceImported`, and `RoleRepositoryPort.save`, change from `void` to returning the stored
  record (mapped after a flush, as `User save` does). `insert` stays `void`.

### Schema: one migration per domain

`VERSION BIGINT NOT NULL DEFAULT 0` on each table, in a new migration per domain (`V2__add_version.sql`; after #133, versions are per domain
and names schema-qualified), with a comment saying what the column is for:

- `artifact/backend/domain/analysis/adapter/src/main/resources/…/domain/analysis/adapter/persistence/migration/V2__add_version.sql`: `"ANALYSES"`
- `artifact/backend/domain/settings/adapter/src/main/resources/…/domain/settings/adapter/persistence/migration/V2__add_version.sql`: `"PRESETS"`
- `artifact/backend/domain/identity/adapter/src/main/resources/…/domain/identity/adapter/persistence/migration/V2__add_version.sql`: `"USERS"`, `"ROLES"`

Existing rows get version 0, nothing else changes. `USER_ROLES` gets no column: it is a collection of
the user aggregate, and Hibernate increments the owning `USERS` row's version when the collection
changes.

### Entities

`@Version @Column(name = "VERSION", nullable = false) private Long version;` with getter and setter on
`AnalysisEntity`, `PresetEntity`, `UserEntity`, `RoleEntity`. A wrapper type, so Spring Data's
`isNew()` treats `version == null` as new (persist) and anything else as existing (merge).
`AnalysisEntity` keeps its own `Persistable.isNew()`.

The MapStruct mappers map `version` by name in both directions; it is not ignored like the audited
fields, since the incoming version is what the write is checked against.

### Adapters

| Adapter | How the check happens |
|---|---|
| `JpaPresetRepositoryAdapter#save` | Unchanged `saveAndFlush`: a `null` version persists (an existing ID fails), any other version merges, and Hibernate rejects a detached entity whose version differs from the stored one |
| `JpaRoleRepositoryAdapter#save` | The same, and returns `toRole.map(saved)` |
| `JpaUserRepositoryAdapter#save` | Loads the managed entity as today. Before `copyOnto`, compares `incoming.getVersion()` with `existing.getVersion()`; a mismatch (a `null` incoming version included) throws `ObjectOptimisticLockingFailureException(UserEntity.class, id)`. A missing row with a non-null version throws the same (the user was deleted meanwhile). JPA forbids changing the version of a managed entity, hence the explicit compare. Ends with `repository.saveAndFlush(entity)` (the managed entity, or the new one) and maps what it returns; the flush's `UPDATE … WHERE VERSION = ?` covers the window between the load and the flush |
| `JpaAnalysisRepositoryAdapter#update`, `#replaceImported` | Same as users: load, compare `analysis.version()` with the managed entity's, throw on a mismatch, copy, `repository.saveAndFlush(stored)`, return the mapped result |
| `JpaAnalysisRepositoryAdapter#insert` | Unchanged: the record's version is `null` and the persist sets 0 |

**Every write that must reach the database in the call ends with `saveAndFlush`** (developer's decision
in review): never a `repository.save(...)`, or a change to a managed entity, followed by a separate
`repository.flush()`. One call, and the entity it returns is the one mapped back. For a managed entity
it is a merge onto itself followed by the flush, so the behaviour is unchanged.
`PersistenceArchitectureTest.writes_use_save_and_flush` forbids calling `flush()` on a Spring Data
repository in production code.

The compare-and-throw is the same few lines in two modules. It stays local in each adapter (a private
helper), as the persistence doc says for `isUniqueViolation`; it moves to
`:backend:library:persistence` only if a third adapter needs it.

### Cores: an error code, translated where the write is called

- `AnalysisError.CONCURRENT_UPDATE`, `SettingsError.CONCURRENT_UPDATE`. The services catch
  `OptimisticLockingFailureException` around their repository writes and throw their domain
  exception with that code and `Map.of("id", <id>)`:
  - `AnalysisService`: one private helper around `repository.update(...)` (used by `dispatch`, `end`,
    `Sink.onEvent`). Its in-process `lock` keeps a single instance from ever hitting it; the check
    matters once two writers share the database.
  - `ExternalAnalysisService#register`: around `replaceImported`. `Analysis.imported(...)` takes the
    version as a parameter (`null` for a new import, `current.version()` for a refresh), so the
    `refreshed.equals(current)` "unchanged" check keeps working. The registration returns the record
    `replaceImported` returned.
  - `PresetService#updatePreset(PresetId id, String name, String payload, Long expectedVersion)`
    (`ManagePresetsUseCase` changes accordingly): loads the preset (404 as today), then saves it with
    `expectedVersion` when the caller gave one, else with the loaded version. A different stored
    version fails with `CONCURRENT_UPDATE`.
- **Identity** has no service that writes users or roles yet (no login, no admin API). Its ports
  promise the exception; `IdentityError` gets no code until a service needs to translate it (see Out
  of scope).

### BFF and API

- `AnalysisExceptionToApiExceptionMapper`, `SettingsExceptionToApiExceptionMapper`:
  `CONCURRENT_UPDATE -> HttpStatus.CONFLICT`, error code `concurrent_update`.
- `PresetDto` gets `version` (`Long`); `SavePresetRequest` gets an optional `version`. `PUT
  /api/presets/{id}` passes it to `updatePreset`; `POST` ignores it. Without it, a `PUT` applies to
  what is stored (as today), so existing clients keep working: an additive, non-breaking change. The
  analysis API exposes no version: no endpoint writes an analysis from a client's copy.
- `SettingsApiDelegateImpl#updatePreset` passes `request.version()`; `PresetToPresetDtoMapper` maps
  `version` by name.
- `artifact/frontend/openapi.json` and `src/shared/api/schema.d.ts` regenerated (`mise run api-types`).

### Frontend

- `features/settings/api.ts`: `updatePreset(id, name, values, version)` sends `version`.
- `PresetManager.vue#rename` passes `preset.version`; on an error it still shows `errorLabel(e)` and
  now also reloads the list (`load()`), so the next rename starts from the current version.
- `shared/i18n/locales/en.json` and `tr.json`: `errors.concurrent_update`, e.g. "Someone else changed
  this in the meantime. Reload and try again." (and the Turkish translation).

### Architecture rule

`PersistenceArchitectureTest.entities_have_a_version`: every `@Entity` class has a field annotated
`jakarta.persistence.Version`. All four entities are updated in place; an insert-only table added
later would need an exception written into the rule and the doc. Show in the pull request description
that the rule fails when `@Version` is removed from one entity (as `docs/coding-convention/README.md`
asks for a changed rule).

## Work packages

### WP1: Schema, entities and adapters

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/analysis/adapter/src/main/resources/…/domain/analysis/adapter/persistence/migration/V2__add_version.sql` (new)
  - `artifact/backend/domain/settings/adapter/src/main/resources/…/domain/settings/adapter/persistence/migration/V2__add_version.sql` (new)
  - `artifact/backend/domain/identity/adapter/src/main/resources/…/domain/identity/adapter/persistence/migration/V2__add_version.sql` (new)
  - `…/analysis/adapter/persistence/entity/AnalysisEntity.java`, `…/analysis/adapter/persistence/JpaAnalysisRepositoryAdapter.java`
  - `…/settings/adapter/persistence/entity/PresetEntity.java`, `…/settings/adapter/persistence/JpaPresetRepositoryAdapter.java`
  - `…/identity/adapter/persistence/entity/UserEntity.java`, `RoleEntity.java`, `JpaUserRepositoryAdapter.java`, `JpaRoleRepositoryAdapter.java`
  - the persistence mappers only if MapStruct needs an explicit mapping (it should map `version` by name)
  - the records and ports of WP2 (the adapters compile against them; WP1 and WP2 land together)
- **Steps**:
  - [x] Write the three migrations with comments.
  - [x] Add `@Version Long version` to the four entities.
  - [x] Implement the checks of the adapter table above; `update`, `replaceImported` and `Role save` return the stored record.
- **Tests** (all on PostgreSQL, `@JpaAdapterTest`, through the port):
  - `JpaAnalysisRepositoryAdapterTest`, `JpaPresetRepositoryAdapterTest`, `JpaIdentityRepositoryTest`
    (users and roles): per entity, a test that reads a record twice, writes the first copy (succeeds,
    version +1), then writes the second, stale copy: it throws `OptimisticLockingFailureException`,
    and a read afterwards returns the first write unchanged. For analyses, both `update` and
    `replaceImported`. Plus: a new record starts at version 0; each successful write increments it and
    the returned record carries it; saving a `null`-version record with an existing ID fails.
  - Existing expectations gain the version (e.g. `savesUpdatesAndDeletes` asserts 0, then 1).
  - Migration without loss (after #133): `V2AnalysisVersionMigrationTest`, `V2SettingsVersionMigrationTest`
    and `V2IdentityVersionMigrationTest`, each with its own Flyway over the real migrations plus a
    test-only seed `persistence/seed/V1_1__seed_rows_before_version.sql`, read the V1 rows back
    unchanged at version 0.
  - `DatabaseNamingTest` covers the new column names unchanged.

### WP2: Domain records, ports and services

- **Status**: done
- **Depends on**: none (lands with WP1)
- **Files**:
  - `…/analysis/core/model/Analysis.java`, `…/analysis/core/outbound/persistence/AnalysisRepositoryPort.java`,
    `…/analysis/core/exception/AnalysisError.java`, `…/analysis/core/service/AnalysisService.java`,
    `…/analysis/core/service/ExternalAnalysisService.java`
  - `…/settings/core/model/Preset.java`, `…/settings/core/outbound/persistence/PresetRepositoryPort.java`,
    `…/settings/core/exception/SettingsError.java`, `…/settings/core/inbound/ManagePresetsUseCase.java`,
    `…/settings/core/service/PresetService.java`
  - `…/identity/core/model/User.java`, `Role.java`, `…/identity/core/outbound/persistence/UserRepositoryPort.java`, `RoleRepositoryPort.java`
  - every `new Analysis(` / `new Preset(` / `new User(` / `new Role(` in main and test code (about 40 calls in 13 files)
- **Steps**:
  - [x] Add `Long version` (last component) to the four records, with the Javadoc semantics; carry it through every transition.
  - [x] `Analysis.queued(...)` gives `null`; `Analysis.imported(id, version, external)`.
  - [x] Port Javadoc and return types as in the design.
  - [x] The error codes and the translation in `AnalysisService`, `ExternalAnalysisService`, `PresetService`.
- **Tests**:
  - `AnalysisServiceTest`: a repository `update` that throws `OptimisticLockingFailureException`
    surfaces as `AnalysisException` with `CONCURRENT_UPDATE`.
  - `ExternalAnalysisServiceTest`: a refresh with unchanged files stays `UNCHANGED` (the version is
    carried), and a stale `replaceImported` surfaces as `CONCURRENT_UPDATE`.
  - `PresetServiceTest`: `updatePreset` saves with the given expected version, or the loaded one
    when none is given; a stale save fails with `CONCURRENT_UPDATE`; an unknown ID is still
    `PRESET_NOT_FOUND`.

### WP3: BFF, API and frontend

- **Status**: done
- **Depends on**: WP2
- **Files**:
  - `artifact/backend/bff/api/src/main/java/…/bff/controller/api/model/PresetDto.java`, `SavePresetRequest.java`
  - `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/SettingsApiDelegateImpl.java`
  - `…/bff/delegate/impl/mapper/error/AnalysisExceptionToApiExceptionMapper.java`, `SettingsExceptionToApiExceptionMapper.java`
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated)
  - `artifact/frontend/src/features/settings/api.ts`, `artifact/frontend/src/features/settings/components/PresetManager.vue`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [x] DTO fields, delegate and mappers.
  - [x] Regenerate the API types with `mise run api-types` (needs `mise run dev`).
  - [x] Frontend: send the version, reload on failure, translate the code.
- **Tests**:
  - A Spring Boot test of the preset API (next to `AnalysesApiIntegrationTest` in `artifact/backend/src/test`,
    e.g. `PresetsApiIntegrationTest`): create a preset (version 0), rename it with version 0 (200,
    version 1), rename again with version 0: 409 with `errorCode` `concurrent_update`, and a `GET`
    shows the first rename. A `PUT` without `version` still succeeds.
  - The two exception mappers: `CONCURRENT_UPDATE` maps to 409 / `concurrent_update` (unit tests
    next to any existing mapper tests, or in the API test above).
  - Frontend: `pnpm lint` and the type-check; the e2e settings test (presets) keeps passing unchanged.

### WP4: Architecture rule and documentation

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java`
  - `docs/coding-convention/backend-java-persistence.md`, `docs/coding-convention/README.md`, `README.md`
- **Steps**:
  - [x] Add `entities_have_a_version`; check it fails with `@Version` removed from one entity, and say so in the PR.
  - [x] Update the documents (see "Docs to update").
- **Tests**: the new ArchUnit rule itself.

### WP5: saveAndFlush instead of save/change + flush

- **Status**: done
- **Depends on**: WP1, WP4
- **Files**:
  - `…/identity/adapter/persistence/JpaUserRepositoryAdapter.java`, `…/analysis/adapter/persistence/JpaAnalysisRepositoryAdapter.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java`
  - `docs/coding-convention/backend-java-persistence.md`
- **Steps**:
  - [x] `JpaUserRepositoryAdapter#save`, `JpaAnalysisRepositoryAdapter#update` and `#replaceImported` end with `repository.saveAndFlush(entity)` and map the returned entity; no `repository.flush()` left.
  - [x] Add `writes_use_save_and_flush` (no call to `flush()` on a Spring Data repository in production code); check it fails on one, and say so in the PR.
  - [x] Persistence doc: the rule under "Repositories and adapters", the adapters' description under "Optimistic locking", and a row in "Where it is checked".
- **Tests**: the existing repository tests (stale writes, versions, returned records) unchanged and green; the new ArchUnit rule.

## Tests

- Repository tests per entity (analysis, preset, user, role) prove the acceptance criterion: a stale
  write fails and leaves the newer row unchanged.
- The migration tests prove existing rows are migrated without loss (version 0, every other field
  unchanged); `ddl-auto=validate` in every adapter and Spring Boot test proves entities match the new
  columns.
- Service unit tests prove the translation to `CONCURRENT_UPDATE`; the preset API test proves the 409
  end to end.
- `PersistenceArchitectureTest.entities_have_a_version` keeps every future entity versioned;
  `writes_use_save_and_flush` keeps writes to one `saveAndFlush`.
- `mise run check` and `mise run build` (CI) run all of them.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-persistence.md` | New section "Optimistic locking": the `VERSION` column per updated-in-place table, `@Version Long` on every entity, the version as a field of the domain record (`null` before the first save, owned by persistence, never changed by the core), why it travels through the core and not only the adapter, the port promise (`OptimisticLockingFailureException`), the adapters' load-compare-flush for managed entities vs the merge check, the translation to `CONCURRENT_UPDATE` in the services and 409 in the BFF, and that writes return the stored record. Update "Mapping domain types" (a row for the version), "Repositories and adapters" (upsert and partial updates now check the version), "Tests" (the stale-write test per entity) and "Where it is checked" (`entities_have_a_version`, `writes_use_save_and_flush`). "Repositories and adapters" also gains the rule that a write ends with `saveAndFlush`, never a separate `flush()` |
| Text | `docs/coding-convention/backend-database-naming.md` | None: the column follows the existing rules; the migration list there is by example only |
| Text | `docs/coding-convention/README.md` | The persistence row's "Covers" mentions optimistic locking; the Database naming row's test list gains the three `V2*VersionMigrationTest`s |
| Text | `README.md`, section around "**Save as preset** … renames or deletes them" | One sentence: a rename of a preset someone else changed meanwhile fails with a message and the list reloads |
| API | `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | Regenerated: `PresetDto.version`, `SavePresetRequest.version` |
| Screenshot | none | The Settings page looks the same: the version is not shown |

## Out of scope

- **Identity's error code and 409**: no service or API writes users or roles yet. The login / admin
  work that adds them adds `IdentityError.CONCURRENT_UPDATE`, its translation and the 409, following
  the persistence document.
- **Versions on the analysis API** and `If-Match` / `ETag` headers: no client writes an analysis from
  its own copy. A version in the request body, as for presets, is enough for now.
- **Retrying** a stale write automatically: every caller gets the error; none retries.
- Deleting with a version check (`DELETE /api/presets/{id}` stays unconditional).
