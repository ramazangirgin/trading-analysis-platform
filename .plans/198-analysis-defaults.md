# Plan: Platform analysis defaults: permissions, storage and API

- **Issue**: #198 (Admin-defined provider, models and debate rounds for analyses; only permitted users override them per analysis)
- **Plan**: 1 of 2 for this issue; depends on: none
- **Version bump**: minor (a new feature; the existing endpoints and request shapes are unchanged)

## Goal

An admin can save the platform's LLM provider, deep- and quick-thinking models and both debate round
counts, and read them back with their optimistic-lock version. The two new permissions
(`analysis:defaults:manage`, `analysis:override-models`) exist, are granted to the built-in ADMIN
role and are selectable on custom roles. Starting an analysis is deliberately not changed in this
plan: plan 2 merges after this one and changes it together with the New analysis page, so the
current page keeps working against the unchanged start endpoint in between.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend identity domain | backend-java-package-structure.md (`Permission` in `domain.identity.core.model`, the `PermissionDto` mirror in `bff.controller.api.model`); repository-shared-types.md (domain enum is the single source, DTO mirror with `@JsonValue` key, OpenAPI carries the values to the frontend, a database enum mirror guarded by a test); backend-database-naming.md and backend-java-persistence.md (migrations, uppercase quoted names) |
| Backend settings domain | backend-java-package-structure.md (core model/port, adapter persistence entity/repository/mappers); backend-java-persistence.md (entities, `@Version` optimistic locking with `Long version` on the record, `saveAndFlush`, one schema per domain, Flyway migrations per domain, conventions test, the stale-write test per entity); backend-database-naming.md |
| Backend orchestration | backend-java-package-structure.md (a use case spanning several domains lives in `orchestration`, one sub-package per feature with only `inbound`, `service`, `model`; `domains_are_independent`) |
| Backend BFF | backend-java-rest-api.md (a 200-only endpoint returns the DTO, never `ResponseEntity`; every error is an exception mapped by `DomainExceptionHandler` and the `*ExceptionToApiExceptionMapper`, never caught in a delegate); `@RequiresPermission` on the delegate method (`TAP-A1`); mappers named `SourceToTargetMapper` |
| Frontend | frontend-folder-structure.md (`features/auth/model/permissions.ts`, `features/roles/model/permissionGroups.ts`, `shared/i18n/locales/*.json`); repository-shared-types.md (`PERMISSIONS` checked with `satisfies Record<string, Permission>`; `ALL_PERMISSIONS` fails the type-check while a generated value is missing) |
| OpenAPI | repository-shared-types.md: `artifact/frontend/openapi.json` is checked in; `mise run api-types` regenerates it and `shared/api/schema.d.ts` from the running backend |

## Design

### Identity: the two permissions

- Add to `Permission` (`domain.identity.core.model`): `ANALYSIS_DEFAULTS_MANAGE("analysis:defaults:manage")`,
  `ANALYSIS_OVERRIDE_MODELS("analysis:override-models")`. Add the same constants with the same
  `@JsonValue` keys to `PermissionDto` (`bff.controller.api.model`). The two MapStruct
  `PermissionToPermissionDtoMapper` / `PermissionDtoToPermissionMapper` map by constant name and need
  no change.
- Identity migration `V7__add_analysis_permissions.sql`: one `ALTER TYPE "IDENTITY"."PERMISSION"
  ADD VALUE` per constant (labels are the Java constant names, not the keys, as the V1 comment
  states). This is the first `ADD VALUE` of the code base.
- `V8__grant_analysis_permissions_to_admin.sql`: append both to ADMIN's array —
  `UPDATE "IDENTITY"."ROLES" SET "PERMISSIONS" = "PERMISSIONS" || ARRAY['ANALYSIS_DEFAULTS_MANAGE',
  'ANALYSIS_OVERRIDE_MODELS']::"IDENTITY"."PERMISSION"[] WHERE "ID" = 'ADMIN';` (the fixed ID V3
  gives the role). Two migrations, because PostgreSQL does not let a transaction use an enum value
  that the same transaction added, and Flyway runs each migration in a transaction of its own. The
  V3 insert materialised ADMIN's array from `ENUM_RANGE` at insert time, so later enum values are
  not picked up without V8. ANALYST and VIEWER are not granted either (the issue). A custom role
  gets them through `/api/roles` like every other permission.

### Settings: the stored `AnalysisDefaults`

- Settings core model: `AnalysisDefaults(String llmProvider, String deepThinkLlm, String
  quickThinkLlm, int maxDebateRounds, int maxRiskDiscussRounds, Long version)` — `version` is
  `null` while nothing is stored and set on every record the port returns, like `Preset.version`.
- Settings core outbound port `AnalysisDefaultsRepositoryPort` in `outbound/persistence`:
  `Optional<AnalysisDefaults> find()` and `AnalysisDefaults save(AnalysisDefaults defaults)`. Its
  Javadoc promises: a `null` version inserts the row; a version that differs from the stored row's,
  **and a `null` version while the row exists**, fail with Spring's
  `OptimisticLockingFailureException` and leave the row unchanged; `save` returns the stored record
  with its new version.
- Settings adapter (`domain.settings.adapter.persistence`):
  - `entity/AnalysisDefaultsEntity` on `"SETTINGS"."ANALYSIS_DEFAULTS"`: a plain `@Id` `String` on
    `ID`, always `"DEFAULTS"` (a one-row table; the domain record has no ID, so there is no ID
    wrapper to embed), columns `LLM_PROVIDER`, `DEEP_THINK_LLM`, `QUICK_THINK_LLM`,
    `MAX_DEBATE_ROUNDS`, `MAX_RISK_DISCUSS_ROUNDS`, `@Version VERSION`.
  - `AnalysisDefaultsJpaRepository`, and `JpaAnalysisDefaultsRepositoryAdapter`: `find` maps the
    row or empty (`@Transactional(readOnly = true)`); `save` (`@Transactional`) merges with
    `saveAndFlush` like `JpaPresetRepositoryAdapter`. A `null`-version save on an existing row would
    otherwise fail with a generic `DataIntegrityViolationException` (the primary key, see
    `JpaPresetRepositoryAdapterTest.savingWithoutAVersionFailsOnAnExistingId`): the adapter checks
    for the row first and throws `OptimisticLockingFailureException`, and translates the primary-key
    violation of a concurrent first insert into the same exception — the only constraint of the
    table besides `NOT NULL`, which the core guarantees.
  - `mapper/AnalysisDefaultsToAnalysisDefaultsEntityMapper` (sets the constant ID) and
    `mapper/AnalysisDefaultsEntityToAnalysisDefaultsMapper`.
- Settings migration `V3__create_analysis_defaults.sql`: the table with a named
  `"ANALYSIS_DEFAULTS_PK"`, every column `NOT NULL`, `"VERSION" BIGINT NOT NULL DEFAULT 0`, and a
  comment on the table explaining the one row with the fixed ID. (Settings Flyway versions are per
  domain; presets used V1 and V2.)

### Orchestration: `ManageAnalysisDefaultsUseCase`

The use case needs both the stored settings and the catalog, and domains are independent, so it
lives in a new orchestration feature `orchestration.analysisdefaults` — `inbound/ManageAnalysisDefaultsUseCase`
and the package-private `service/AnalysisDefaultsService` — not in the settings core as the issue
says. The orchestration module already depends on `settings:core` and `catalog:core`.

- `AnalysisDefaults get()`: the stored row when one exists, without asking the catalog; otherwise
  the catalog's `ModelDefaults` (upstream's defaults including its `TRADINGAGENTS_*` overrides) and
  `1 / 1` rounds with `version` null, so a fresh install works without an admin step. Only the
  fallback calls `GetCatalogUseCase`, so only then can a `CatalogUnavailableException` surface (the
  BFF already answers it with 503 `catalog_unavailable`).
- `AnalysisDefaults update(AnalysisDefaults defaults, Long expectedVersion)`:
  - validate against the catalog: the provider must exist in it, both models must be listed for
    that provider (its `deepModels` and `quickModels`), and both rounds must be in 1..10, the range
    the start request accepts today (`AnalysisSpecValidator.MAX_ROUNDS`); otherwise
    `SettingsException(SettingsError.INVALID_ANALYSIS_DEFAULTS, ..., Map.of("field", <the field>))`;
  - save with `expectedVersion` as given. Unlike presets, `null` does not mean "apply to what is
    stored": it is the version a client reads while nothing is stored, so it means "insert", and a
    row saved meanwhile makes it fail. That is what makes the second of two admins get 409 also on
    the very first save;
  - translate `OptimisticLockingFailureException` into `SettingsException(SettingsError.CONCURRENT_UPDATE, ...)`
    as `PresetService.updatePreset` does.
- New code `INVALID_ANALYSIS_DEFAULTS` in `SettingsError`.

### BFF

- `SettingsApiController` / `SettingsApiDelegate` / `SettingsApiDelegateImpl`:
  - `GET /api/settings/analysis-defaults` → `AnalysisDefaultsDto` (the five values and `version`,
    `null` while nothing is stored); `@RequiresPermission({Permission.ANALYSIS_RUN,
    Permission.ANALYSIS_DEFAULTS_MANAGE})` (any of them): the New analysis page reads it with
    `analysis:run`, and the Settings card of plan 2 with the manage permission, also on a custom role
    without `analysis:run`.
  - `PUT /api/settings/analysis-defaults` → `AnalysisDefaultsDto` (200, the stored values with
    their new version); body `SaveAnalysisDefaultsRequest` (`@NotBlank` provider and both models,
    `@NotNull` both rounds, `Long version`: the version from the GET, `null` when it was `null`);
    `@RequiresPermission(Permission.ANALYSIS_DEFAULTS_MANAGE)`.
  - New MapStruct mapper `AnalysisDefaultsToAnalysisDefaultsDtoMapper` in `bff.delegate.impl.mapper`.
- `SettingsExceptionToApiExceptionMapper` gains `INVALID_ANALYSIS_DEFAULTS -> 400`
  (`invalid_analysis_defaults`); `CONCURRENT_UPDATE -> 409 concurrent_update` already exists. The
  delegate catches nothing.
- `mise run api-types`: `artifact/frontend/openapi.json` and `shared/api/schema.d.ts` gain the two
  endpoints, the DTOs and the two new keys in the generated `Permission` union.

### Frontend housekeeping for the new permissions

No page changes in this plan; only what the new keys need:

- `features/auth/model/permissions.ts`: `analysisDefaultsManage: 'analysis:defaults:manage'` and
  `analysisOverrideModels: 'analysis:override-models'`.
- `features/roles/model/permissionGroups.ts`: both keys in `ALL_PERMISSIONS` (after
  `analysis:delete`). `areaOf` groups by the key's first segment, so both land in the analysis
  group of the Roles checklist without further change.
- `shared/i18n/locales/en.json` and `tr.json`: `permissions.keys` labels for both keys.

## Work packages

### WP1: Identity permissions

- **Status**: done
- **Depends on**: none
- **Files**: `domain/identity/core/.../model/Permission.java`; `bff/api/.../model/PermissionDto.java`;
  `domain/identity/adapter/src/main/resources/.../migration/V7__add_analysis_permissions.sql`,
  `V8__grant_analysis_permissions_to_admin.sql`
- **Steps**:
  - [x] add the two constants to `Permission` (keys `analysis:defaults:manage`, `analysis:override-models`)
  - [x] add them to `PermissionDto` with the same `@JsonValue` keys
  - [x] V7: `ALTER TYPE "IDENTITY"."PERMISSION" ADD VALUE` for each constant, in declaration order
  - [x] V8: append both to the role with ID `ADMIN` (ANALYST and VIEWER unchanged)
- **Tests**: `PermissionTest` (keys round-trip and are unique, unchanged); `JpaIdentityRepositoryTest`
  (the database enum mirrors `Permission`: green only with V7); `RolePermissionsIntegrationTest`
  (ADMIN's `me` returns every `Permission::key`: green only with V8; ANALYST's and VIEWER's exact
  sets stay unchanged).

### WP2: Settings storage

- **Status**: done
- **Depends on**: none (parallel with WP1)
- **Files**: settings core `model/AnalysisDefaults.java`, `outbound/persistence/AnalysisDefaultsRepositoryPort.java`;
  settings adapter `entity/AnalysisDefaultsEntity.java`, `AnalysisDefaultsJpaRepository.java`,
  `JpaAnalysisDefaultsRepositoryAdapter.java`, the two mappers in `mapper/`;
  `.../adapter/persistence/migration/V3__create_analysis_defaults.sql`
- **Steps**:
  - [x] the model and the port, with the contract above
  - [x] the entity, the mappers and the adapter (the `null`-version check and translation)
  - [x] the migration
- **Tests**: `JpaAnalysisDefaultsRepositoryAdapterTest` (`@JpaAdapterTest`, modelled on
  `JpaPresetRepositoryAdapterTest`): `find` on an empty table is empty; a `null`-version save
  inserts the row at version 0; a save at the stored version updates it and the version rises; the
  stale-write test (read twice, write the first copy, the stale second write fails with
  `OptimisticLockingFailureException`, a read returns the first write); a `null`-version save while
  the row exists fails with `OptimisticLockingFailureException` and changes nothing.
  `SettingsPersistenceConventionsTest` covers the new entity and migration as it is and must stay
  green.

### WP3: Orchestration use case

- **Status**: done
- **Depends on**: WP2
- **Files**: `orchestration/.../orchestration/analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`,
  `orchestration/.../orchestration/analysisdefaults/service/AnalysisDefaultsService.java`;
  settings core `exception/SettingsError.java` (`INVALID_ANALYSIS_DEFAULTS`)
- **Steps**:
  - [x] `get()`: the stored row, else the catalog's `ModelDefaults` and `1 / 1` with `version` null
  - [x] `update()`: the catalog validation with the field param; the save with the version as
    given; `CONCURRENT_UPDATE` on `OptimisticLockingFailureException`
- **Tests**: `AnalysisDefaultsServiceTest` (unit, fakes for the port and `GetCatalogUseCase`): the
  fallback while nothing is stored; the stored row returned without asking the catalog; each
  validation failure (unknown provider, a model not listed for the provider, rounds 0 and 11) →
  `INVALID_ANALYSIS_DEFAULTS` with its field; a stale version and a `null` version while a row
  exists → `CONCURRENT_UPDATE`; a valid update returns the stored values with the new version.

### WP4: BFF endpoints, OpenAPI, frontend housekeeping

- **Status**: done
- **Depends on**: WP1, WP3
- **Files**: `bff/api/.../model/AnalysisDefaultsDto.java`, `SaveAnalysisDefaultsRequest.java`;
  `SettingsApiController.java`, `SettingsApiDelegate.java`; `bff/impl/.../SettingsApiDelegateImpl.java`;
  `bff/impl/.../mapper/AnalysisDefaultsToAnalysisDefaultsDtoMapper.java`;
  `bff/impl/.../mapper/error/SettingsExceptionToApiExceptionMapper.java`;
  `artifact/backend/src/test/.../TestSession.java` (`validBody`), `RolePermissionsIntegrationTest.java`,
  the new `AnalysisDefaultsApiIntegrationTest.java`;
  `artifact/frontend/openapi.json` and `artifact/frontend/src/shared/api/schema.d.ts` (generated);
  frontend `features/auth/model/permissions.ts`, `features/roles/model/permissionGroups.ts`,
  `shared/i18n/locales/en.json`, `shared/i18n/locales/tr.json`
- **Steps**:
  - [x] the two endpoints with their `@RequiresPermission`
  - [x] the DTOs, the mapper and the `INVALID_ANALYSIS_DEFAULTS -> 400` case
  - [x] `mise run api-types`
  - [x] the frontend permission entries, `ALL_PERMISSIONS` and the i18n labels (en and tr)
- **Tests**:
  - new `AnalysisDefaultsApiIntegrationTest` (modelled on `PresetsApiIntegrationTest`:
    `@SpringBootTest`, real PostgreSQL, `TestSession` over HTTP). Its runner script answers
    `catalog` (the runner is called as `<script> catalog`) with a small catalog JSON in the shape of
    docs/event-protocol.md section 1, two providers with their models and defaults; the scripts of
    the other integration tests answer every command with run events, so their catalog is
    unavailable. Cases: GET while nothing is stored returns the catalog defaults, `1 / 1` and
    `version` null; PUT with `version` null stores the values at version 0; two admins who both
    read `version` null: the second PUT → 409 `concurrent_update` and the first one's values stay;
    a PUT with a stale version → 409; an unknown provider, a model not listed for the provider and
    rounds 0 / 11 → 400 `invalid_analysis_defaults` with the field; GET with a custom role holding
    only `analysis:defaults:manage` → 200;
  - `TestSession.validBody` gets a valid body for `PUT /api/settings/analysis-defaults` (without it,
    `EndpointPermissionsIntegrationTest`'s user without roles gets 400 from validation instead of
    403);
  - `RolePermissionsIntegrationTest`: ANALYST may GET the defaults and gets 403 `forbidden` on the
    PUT; VIEWER gets 403 on both;
  - frontend: `permissionGroups.spec.ts` ("every permission in exactly one group") stays green with
    the two keys; `vue-tsc` (`mise run check`) proves `ALL_PERMISSIONS` and `PERMISSIONS` match the
    generated `Permission`.

## Tests

WP1's tests guarded by the database enum and ADMIN's permissions, WP2's repository tests with the
stale writes, WP3's service unit tests, and WP4's HTTP integration tests. Together they prove this
plan's part of the issue's acceptance criteria: an admin saves and reads the platform values; the
second of two admins saving at the same time gets 409, also on the first save; a provider or model
not in the catalog is refused in the admin settings; while nothing is saved, the catalog defaults
and `1 / 1` are answered. The start-flow and UI criteria belong to plan 2.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | README.md, "Login", the permissions-per-endpoint-group table | `GET /api/settings/analysis-defaults` → `analysis:run` or `analysis:defaults:manage`; `PUT /api/settings/analysis-defaults` → `analysis:defaults:manage` |
| Text | README.md, "Login", a bullet after the `/api/roles` one | the analysis-defaults endpoints: the DTO's fields, the catalog fallback with `version` null while nothing is saved, `version` on the PUT (`null` only for the first save), 400 `invalid_analysis_defaults` with `params.field`, 409 `concurrent_update`, 503 `catalog_unavailable` for the fallback |
| Text | README.md, the built-in roles paragraph after that table | the two new permissions: ADMIN holds them, ANALYST and VIEWER do not, custom roles can be given them |
| Text | docs/coding-convention/repository-shared-types.md, "Adding a value", step 4 | the value and its grant are two migrations (`ADD VALUE`, then the grant), since a transaction cannot use an enum value it added; name V7 / V8 as the example |
| Text | docs/coding-convention/backend-java-package-structure.md, "Today's adapter packages" | the `domain.settings.adapter.persistence` row gains `JpaAnalysisDefaultsRepositoryAdapter`, `AnalysisDefaultsJpaRepository` |
| Text | docs/coding-convention/backend-java-persistence.md, "Optimistic locking" | `ANALYSIS_DEFAULTS` in the list of tables with `VERSION`, `JpaAnalysisDefaultsRepositoryAdapter` among the merged entities, with its `null`-version rule for the one-row table |
| Generated | `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | regenerated with `mise run api-types` |

Screenshots: none. No page looks different in this plan; the roles screenshot shows the role list,
not the permission checklist.

## Out of scope

- The start flow (the server decides the five values, per-analysis overrides, the 403): plan 2 of
  this issue (`198-analysis-defaults-start`), which merges after this one. `StartAnalysisRequest`
  and the start use case stay untouched here on purpose.
- The Settings page's Analysis defaults card, the New analysis page's read-only or editable fields,
  preset handling, e2e tests and screenshots: plan 2.
- Per-user or per-role defaults, limiting which providers or models an overriding user may pick,
  cost estimates (#37).
