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
| Backend settings domain | backend-java-package-structure.md (core model/port/service, adapter persistence entity/repository/mappers); backend-java-persistence.md (entities, `@Version` optimistic locking with `Long version` on the record, one schema per domain, Flyway migrations per domain, conventions test); backend-database-naming.md |
| Backend orchestration | backend-java-package-structure.md (a use case spanning several domains lives in `orchestration`, one sub-package per feature; `domains_are_independent`) |
| Backend BFF | backend-java-rest-api.md (a 200-only endpoint returns the DTO, never `ResponseEntity`; every error is an exception mapped by `DomainExceptionHandler` and the `*ExceptionToApiExceptionMapper`, never caught in a delegate); `@RequiresPermission` on the delegate method (`TAP-A1`) |
| Frontend | frontend-folder-structure.md (`features/auth/model/permissions.ts`, `features/roles/model/permissionGroups.ts`, `shared/i18n/locales/*.json`); repository-shared-types.md (`PERMISSIONS` checked with `satisfies Record<string, Permission>`) |
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
  states). A second migration is needed because PostgreSQL forbids using a value added with
  `ADD VALUE` in the same transaction, and Flyway runs each migration in its own.
- `V8__grant_analysis_permissions_to_admin.sql`: append both to ADMIN's array —
  `UPDATE "IDENTITY"."ROLES" SET "PERMISSIONS" = "PERMISSIONS" || ARRAY['ANALYSIS_DEFAULTS_MANAGE',
  'ANALYSIS_OVERRIDE_MODELS']::"IDENTITY"."PERMISSION"[] WHERE "NAME" = 'ADMIN';`. The V3 insert
  materialised ADMIN's array from `ENUM_RANGE` at insert time, so later enum values are not picked
  up automatically. ANALYST and VIEWER are not granted either (the issue). A custom role picks them
  up through `/api/roles` as every other permission.

### Settings: the stored `AnalysisDefaults`

- Settings core model: `AnalysisDefaults(String llmProvider, String deepThinkLlm, String
  quickThinkLlm, int maxDebateRounds, int maxRiskDiscussRounds, Long version)` — `version` is
  `null` on a record the core builds before its first save and set on every record a port returns,
  like `Preset.version`.
- Settings core outbound port `AnalysisDefaultsRepositoryPort` in `outbound/persistence`:
  `Optional<AnalysisDefaults> find()` and `AnalysisDefaults save(AnalysisDefaults defaults)`, with
  the same Javadoc contract as `PresetRepositoryPort.save`: `null` version means insert, so it fails
  on an existing ID; a version that differs from the stored row fails with
  `OptimisticLockingFailureException` and leaves the row unchanged; save returns the stored record
  with its new version.
- Settings adapter (`domain.settings.adapter.persistence`): `AnalysisDefaultsEntity` on
  `"SETTINGS"."ANALYSIS_DEFAULTS"` — `@EmbeddedId AnalysisDefaultsIdEmbeddable` holding the fixed
  row ID `"DEFAULTS"` (a one-row table; the ID stays in the adapter, the domain record has none),
  columns `LLM_PROVIDER`, `DEEP_THINK_LLM`, `QUICK_THINK_LLM`, `MAX_DEBATE_ROUNDS`,
  `MAX_RISK_DISCUSS_ROUNDS` (all `NOT NULL`), `@Version VERSION`; `AnalysisDefaultsJpaRepository`;
  `JpaAnalysisDefaultsRepositoryAdapter` (`find` maps the one row or empty; `save` merges with
  `saveAndFlush`, like `JpaPresetRepositoryAdapter`); entity ↔ model mappers.
- Settings migration `V3__create_analysis_defaults.sql`: the table with a named
  `"ANALYSIS_DEFAULTS_PK"`, `"VERSION" BIGINT NOT NULL DEFAULT 0`, and a comment explaining the
  fixed-ID one-row design. (Settings Flyway versions are per domain; presets used V1 and V2.)

### Orchestration: `ManageAnalysisDefaultsUseCase`

The use case needs both the stored settings and the catalog, and domains are independent, so it
lives in a new orchestration feature `orchestration.analysisdefaults` — inbound
`ManageAnalysisDefaultsUseCase`, package-private `AnalysisDefaultsService` — not in the settings
core. (The issue places it in the settings domain; this is the convention's home for a use case
that spans settings and catalog.)

- `AnalysisDefaults get()`: the stored row when one exists; otherwise the catalog's `ModelDefaults`
  (upstream's defaults including its `TRADINGAGENTS_*` overrides) and `1 / 1` rounds, with
  `version` null, so a fresh install works without an admin step. It calls `GetCatalogUseCase`, so
  a `CatalogUnavailableException` can surface here too (the BFF already maps it to 503).
- `AnalysisDefaults update(AnalysisDefaults defaults, Long expectedVersion)`: validate against the
  catalog — the provider must exist in it, both models must be listed for that provider, and both
  rounds must be in 1..10, the range the start request accepts today
  (`AnalysisSpecValidator.MAX_ROUNDS`) — otherwise `SettingsException(SettingsError.INVALID_ANALYSIS_DEFAULTS, ...,
  Map.of("field", <the offending field>))`. Then save through the port with `expectedVersion`
  (`null` = apply to what is stored, the `PresetService.updatePreset` pattern), translating
  `OptimisticLockingFailureException` to `SettingsException(SettingsError.CONCURRENT_UPDATE, ...)`
  exactly as `PresetService.updatePreset` does.
- New code `INVALID_ANALYSIS_DEFAULTS` in `SettingsError`.
- Add the orchestration module's Gradle dependencies on settings-core and catalog-core if it does
  not declare them yet.

### BFF

- `SettingsApiController` / `SettingsApiDelegate` / `SettingsApiDelegateImpl`:
  - `GET /api/settings/analysis-defaults` → `AnalysisDefaultsDto` (the five values and `version`,
    which is `null` while no row is stored); `@RequiresPermission(Permission.ANALYSIS_RUN)`, so the
    New analysis page can show the values.
  - `PUT /api/settings/analysis-defaults` → `AnalysisDefaultsDto` (200, the stored values with
    their new version); body `SaveAnalysisDefaultsRequest` (`@NotBlank` provider and both models,
    `@NotNull` both rounds, optional `Long version` like `SavePresetRequest.version`);
    `@RequiresPermission(Permission.ANALYSIS_DEFAULTS_MANAGE)`.
  - New MapStruct mapper `AnalysisDefaultsToAnalysisDefaultsDtoMapper` in `bff.delegate.impl.mapper`.
- `SettingsExceptionToApiExceptionMapper` gains `INVALID_ANALYSIS_DEFAULTS -> 400`
  (`invalid_analysis_defaults`); `CONCURRENT_UPDATE -> 409 concurrent_update` already exists, which
  is the "two admins save at the same time: the second gets 409" answer. The delegate does not
  catch anything.
- `mise run api-types`: `artifact/frontend/openapi.json` and `shared/api/schema.d.ts` gain the two
  endpoints, the DTOs and the two new keys in the generated `Permission` union.

### Frontend housekeeping for the new permissions

No page changes in this plan; only the plumbing the new permissions need to exist properly:

- `features/auth/model/permissions.ts`: `analysisDefaultsManage: 'analysis:defaults:manage'` and
  `analysisOverrideModels: 'analysis:override-models'`.
- `features/roles/model/permissionGroups.ts`: `analysis:override-models` in the analysis group,
  `analysis:defaults:manage` in the settings group, so the Roles UI offers both on custom roles.
- `shared/i18n/locales/en.json` and `tr.json`: `permissions.keys` labels for both keys.

## Work packages

### WP1: Identity permissions

- **Depends on**: none
- **Files**: `domain/identity/core/.../model/Permission.java`; `bff/api/.../model/PermissionDto.java`;
  `domain/identity/adapter/src/main/resources/.../migration/V7__add_analysis_permissions.sql`,
  `V8__grant_analysis_permissions_to_admin.sql`
- **Steps**:
  - [ ] add the two constants to `Permission` (keys `analysis:defaults:manage`, `analysis:override-models`)
  - [ ] add them to `PermissionDto` with the same `@JsonValue` keys
  - [ ] V7: `ALTER TYPE "IDENTITY"."PERMISSION" ADD VALUE` for each constant, in declaration order
  - [ ] V8: append both to ADMIN's `"PERMISSIONS"` array (ANALYST and VIEWER unchanged)
- **Tests**: `PermissionTest` (keys round-trip and are unique — unchanged); `JpaIdentityRepositoryTest`
  (the database enum mirrors `Permission` — passes with V7); `RolePermissionsIntegrationTest`
  (ADMIN's `me` returns every `Permission::key` — passes with V8; ANALYST's and VIEWER's exact sets
  are unchanged).

### WP2: Settings storage

- **Depends on**: none (parallel with WP1)
- **Files**: settings core `model/AnalysisDefaults.java`, `outbound/persistence/AnalysisDefaultsRepositoryPort.java`;
  settings adapter `entity/AnalysisDefaultsEntity.java`, `entity/AnalysisDefaultsIdEmbeddable.java`,
  `AnalysisDefaultsJpaRepository.java`, `JpaAnalysisDefaultsRepositoryAdapter.java`,
  `mapper/AnalysisDefaultsToAnalysisDefaultsEntityMapper.java` and its reverse;
  `.../adapter/persistence/migration/V3__create_analysis_defaults.sql`
- **Steps**:
  - [ ] the model and the port (with the optimistic-locking contract)
  - [ ] the entity, the fixed-ID embeddable, the mappers and the adapter
  - [ ] the migration (named PK, `VERSION ... DEFAULT 0`, comment on the fixed-ID row)
- **Tests**: a `@JpaAdapterTest` repository test for the new port, modelled on the settings
  adapter's preset repository test: `find` on an empty table is empty; `save` with a `null` version
  inserts the fixed row; a second save updates it and the version rises; the stale-write test
  (read twice, write the first copy, the second stale write fails with
  `OptimisticLockingFailureException` and the row keeps the first write); a `null`-version save with
  the row present fails. `SettingsPersistenceConventionsTest` picks up the new entity and migration
  automatically and must stay green.

### WP3: Orchestration use case

- **Depends on**: WP2
- **Files**: `orchestration/.../orchestration/analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`,
  `orchestration/.../orchestration/analysisdefaults/service/AnalysisDefaultsService.java`;
  settings core `exception/SettingsError.java` (new code `INVALID_ANALYSIS_DEFAULTS`);
  the orchestration build file if the settings-core or catalog-core dependency is missing
- **Steps**:
  - [ ] `get()`: stored row, else catalog `ModelDefaults` + `1 / 1` rounds with `version` null
  - [ ] `update()`: catalog validation (provider exists, models listed for it, rounds 1..10) →
    `INVALID_ANALYSIS_DEFAULTS` with the field param; save with the version semantics of
    `PresetService.updatePreset`; `CONCURRENT_UPDATE` on a stale write
- **Tests**: `AnalysisDefaultsServiceTest` (unit, fakes for the repository port and the catalog use
  case): fallback when no row, stored row returned, each validation failure → `SettingsException`
  `INVALID_ANALYSIS_DEFAULTS` with the field, stale version → `SettingsException` `CONCURRENT_UPDATE`,
  a valid update stores the values and returns the new version.

### WP4: BFF endpoints, OpenAPI, frontend housekeeping

- **Depends on**: WP1, WP3
- **Files**: `bff/api/.../model/AnalysisDefaultsDto.java`, `SaveAnalysisDefaultsRequest.java`;
  `SettingsApiController.java`, `SettingsApiDelegate.java`; `bff/impl/.../SettingsApiDelegateImpl.java`;
  `bff/impl/.../mapper/AnalysisDefaultsToAnalysisDefaultsDtoMapper.java`;
  `bff/impl/.../mapper/error/SettingsExceptionToApiExceptionMapper.java`;
  `artifact/frontend/openapi.json` and `artifact/frontend/src/shared/api/schema.d.ts` (generated);
  frontend `features/auth/model/permissions.ts`, `features/roles/model/permissionGroups.ts`,
  `shared/i18n/locales/en.json`, `shared/i18n/locales/tr.json`
- **Steps**:
  - [ ] the two endpoints with `@RequiresPermission(ANALYSIS_RUN)` and `@RequiresPermission(ANALYSIS_DEFAULTS_MANAGE)`
  - [ ] the DTOs and the mapper; the `INVALID_ANALYSIS_DEFAULTS -> 400` case
  - [ ] `mise run api-types`
  - [ ] the frontend permission entries, checklist groups and i18n labels (en and tr)
- **Tests**: new `AnalysisDefaultsApiIntegrationTest` (modelled on `PresetsApiIntegrationTest`:
  `@SpringBootTest`, real PostgreSQL, `TestSession` over HTTP): GET without a stored row returns the
  catalog defaults with `version` null; PUT stores and returns the values with their version; PUT
  with a stale version → 409 `concurrent_update`; PUT with an unknown provider, an unlisted model or
  out-of-range rounds → 400 `invalid_analysis_defaults` with the field; GET without `analysis:run`
  and PUT without the manage permission → 403 `forbidden`. Add a `TestSession.validBody` entry for
  the new PUT body and `RolePermissionsIntegrationTest` rows for the new endpoints (GET:
  `analysis:run`; PUT: only the new permission); the `EndpointPermissionsIntegrationTest` sweep then
  stays green.

## Tests

WP1's unit and migration-guarded tests, WP2's `@JpaAdapterTest` repository and stale-write tests,
WP3's service unit tests, and WP4's HTTP integration tests. Together they prove this plan's part of
the issue: an admin saves and reads the platform values; a stale save by a second admin answers
409; a provider or model not in the catalog is refused; a fresh install answers the catalog
fallback. The start-flow and UI acceptance criteria belong to plan 2.

## Docs to update

| What | Where | Change |
|---|---|---|
| Endpoint table | README.md, "Login" section, the permissions-per-endpoint-group table | add `GET /api/settings/analysis-defaults` → `analysis:run` and `PUT /api/settings/analysis-defaults` → `analysis:defaults:manage` |
| Roles paragraph | README.md, the paragraph after that table | say that the two new permissions exist, are held by ADMIN among the built-in roles, and can be granted to custom roles |
| Generated API types | `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | regenerated with `mise run api-types` |

Screenshots: none — no page looks different in this plan (the roles screenshot does not show the
permission checklist).

## Out of scope

- The start flow (the server decides the five values, per-analysis overrides, the 403): plan 2 of
  this issue (`198-analysis-defaults-start`), which merges after this one. `StartAnalysisRequest`
  and the start use case stay untouched here on purpose.
- The Settings page's Analysis defaults card, the New analysis page's read-only or editable fields,
  preset handling, e2e tests and screenshots: plan 2.
- Per-user or per-role defaults, limiting which providers or models an overriding user may pick,
  cost estimates (#37).
