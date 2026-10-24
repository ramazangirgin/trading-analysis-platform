# Plan: Presets belong to the user who created them

- **Issue**: #92 (Ownership: presets belong to the user who created them)
- **Plan**: 1 of 1 for this issue; depends on: none (#91 analysis ownership, #192 sharing analyses
  and #89 user management are merged)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md). The
  `GET /api/presets` response gains `owner` and the endpoint gains an optional `allUsers`
  parameter; callers that send neither get their own presets, which is the intended change.

## Goal

Every preset has an owner: the user who saved it, and the first admin for the presets that existed
before this change. A user lists, renames and deletes only their own presets, and gets **404** for
anyone else's, as for a preset that does not exist. A user with `preset:read:all` (ADMIN) can list,
rename and delete every preset, and **Settings → Presets** gives them an **All users** switch and
an **Owner** column. The New Analysis page keeps listing only your own presets, so a preset can
only be applied by someone who may read it.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: settings domain (core, persistence adapter) | `backend-java-package-structure.md`: value types in `core.model`, the use case in `core.inbound`, the port in `core.outbound.persistence`, the service stays package-private; `backend-java-persistence.md`: Spring Data JPA only (JPQL, no native query), entity and MapStruct mappers in `adapter.persistence.entity` / `mapper`, `saveAndFlush`, `@Version` stays, list filters as `Specification`s; `backend-database-naming.md`: migration `V4__add_owner.sql`, uppercase quoted names, a named index, no foreign key to another domain's schema |
| Backend: identity domain | Unchanged: `Permission.PRESET_READ_ALL` and `UserDirectoryUseCase` (`firstAdmin()`, `usernames(...)`) exist already |
| Backend: orchestration | `orchestration.ownership` already holds `AssignAnalysisOwnersUseCase`; the preset counterpart goes next to it (`orchestration_use_cases_are_concrete_beans`, `orchestration_features_follow_the_domain_core_shape`), because the domains stay independent (`domains_are_independent`) |
| Backend: BFF | `backend-java-rest-api.md`: `200` endpoints return their DTO, `201` and `204` keep `ResponseEntity`; the 404 comes from `SettingsException(PRESET_NOT_FOUND)` through `DomainExceptionHandler` and the existing `SettingsExceptionToApiExceptionMapper`, never a `try` / `catch` in the delegate; `@RequiresPermission` stays on every delegate method (`TAP-A1`); the current user comes from `CurrentUser` (`bff.delegate.impl.config`); the DTO in `bff.controller.api.model` |
| Shared types | `repository-shared-types.md`: `openapi.json` regenerated against a throwaway PostgreSQL, then `mise run api-types` for `shared/api/schema.d.ts`; the frontend uses the generated `Preset` type and the `Permission` key |
| Frontend | `frontend-folder-structure.md`: the call in `features/settings/api.ts`, the column and switch in `features/settings/components/PresetManager.vue`, the column rules as pure logic in `features/settings/model/presetColumns.ts` with its spec, the permission key in `features/auth/model/permissions.ts`, translations in `shared/i18n/locales/en.json`, `tr.json` |
| e2e | `e2e/tests/*.e2e.ts` style: the shared admin's session must not change; users created with unique names through the API (as `ownership.e2e.ts`) |
| Docs | Root `README.md`, `docs/screenshots/presets.png` |

## Design

### Owner in the settings domain

The settings domain does not know the identity domain, so it has its own plain ID of a user, the
same shape as in the analysis domain:

- `core.model.OwnerId(String value)`: the ID of the user a preset belongs to (non-blank).
- `core.model.PresetAccess(OwnerId user, boolean allUsers)`: who is asking and whether they may see
  every preset (`preset:read:all`). One method, `boolean canSee(Preset)`: `allUsers`, or the
  preset's owner equals `user`. A preset without an owner is seen by `allUsers` only.
- `Preset` gets a component `OwnerId owner`, after `payload` and before `updatedAt`. It is `null`
  only on rows from before this change until the startup assignment below has run. `createPreset`
  sets it; `updatePreset` carries the stored one (an update never changes the owner).

### The checks live in the use case

`ManagePresetsUseCase` takes the `PresetAccess` and answers a preset the caller cannot see with the
same `SettingsException(PRESET_NOT_FOUND, "Preset not found: <id>")` as a missing one, so its
existence is not revealed (the BFF answers **404** as today):

| Method | New signature | Rule |
|---|---|---|
| `listPresets` | `listPresets(PresetAccess access, boolean allUsers)` | the caller's own presets; with `allUsers` **and** `access.allUsers()`, everyone's; `allUsers` without the permission is ignored, no error |
| `createPreset` | `createPreset(String name, String payload, OwnerId owner)` | the new preset belongs to `owner` |
| `updatePreset` | `updatePreset(PresetId id, String name, String payload, Long expectedVersion, PresetAccess access)` | NOT_FOUND unless `canSee` |
| `deletePreset` | `deletePreset(PresetId id, PresetAccess access)` | NOT_FOUND unless `canSee`; a preset that vanished meanwhile is NOT_FOUND as today |

Who may change what follows from the two permissions together: the BFF requires `preset:manage` on
create, update and delete, and `canSee` then limits it to the caller's own presets unless they also
hold `preset:read:all`. So an admin changes everyone's presets, an ANALYST only their own, and a
VIEWER (`preset:read` only) can create and change nothing.

The old signatures go away, so the compiler finds every caller. One more inbound port for the
orchestration: `AssignUnownedPresetsUseCase.assignUnowned(OwnerId owner)` (new, `core.inbound`,
implemented by `PresetService`): gives every preset without an owner to `owner`, returns how many.

### Persistence

- `V4__add_owner.sql`: `ALTER TABLE "SETTINGS"."PRESETS" ADD COLUMN "OWNER_ID" TEXT;` (nullable:
  the migration cannot look the first admin up, since no migration references another domain's
  schema, and there is none on a fresh install) and `CREATE INDEX "PRESETS_OWNER_ID_IDX" ON
  "SETTINGS"."PRESETS" ("OWNER_ID");`.
- `PresetEntity` gets `OWNER_ID` through an `OwnerIdAttributeConverter` in `entity` (`@Convert`, not
  `autoApply`), the same way as in the analysis adapter. The MapStruct mappers
  (`PresetToPresetEntityMapper`, `PresetEntityToPresetMapper`) map it by name.
- `PresetRepositoryPort.findAll(OwnerId owner)` (`null`: every owner) replaces `findAll()`; the
  adapter adds a `hasOwner(OwnerId)` `Specification` (`PresetJpaRepository` extends
  `JpaSpecificationExecutor`), as `JpaAnalysisRepositoryAdapter` does.
- `save` keeps working as an upsert, so `updatePreset` builds the new `Preset` with the stored
  owner.
- `PresetRepositoryPort.assignOwnerWhereMissing(OwnerId owner)` returns the number of rows: a
  `@Transactional` adapter method that loads the entities with `OWNER_ID IS NULL` (a derived
  finder), sets the owner and `saveAndFlush`es them, so each row's `VERSION` rises as on any other
  write.

### Orchestration: presets that existed before

`orchestration.ownership.inbound.AssignPresetOwnersUseCase` (a concrete `@Service`, like
`AssignAnalysisOwnersUseCase`) with `assignUnownedToFirstAdmin()` on `ApplicationReadyEvent`: asks
`UserDirectoryUseCase.firstAdmin()` and, when there is one, calls
`AssignUnownedPresetsUseCase.assignUnowned`; it logs the count at INFO when above 0 and a WARN on an
`OptimisticLockingFailureException`, as the analysis one does. This gives every preset of an existing
installation an owner on the first start after the upgrade.

### BFF

- `SettingsApiDelegateImpl` builds the access once per call from `CurrentUser`:
  `new PresetAccess(new OwnerId(currentUser.id().value()), currentUser.has(Permission.PRESET_READ_ALL))`
  and passes it, or the owner, to the use case. The permissions on the methods stay as they are.
- `GET /api/presets` gets the query parameter `allUsers` (boolean, default `false`). Still `200`
  with `List<PresetDto>`.
- `PresetDto` gets `owner` (`PresetOwnerDto { id, username }`, `null` for a preset without an owner).
  The delegate looks the usernames up once per response with `UserDirectoryUseCase.usernames` and
  maps them with `PresetToPresetDtoMapper` (one `map` method stays the rule). Create (`201`), update
  (`200`) and list fill it.
- No new error: 404 is the existing `PRESET_NOT_FOUND` mapping.

### Starting an analysis from a preset

A preset is applied in the browser: New Analysis fills its form from the list `GET /api/presets`
returned and sends the form's values to `POST /api/analyses`. The start request carries no preset
ID, and the backend has no other way to reach a preset, so the issue's "starting an analysis from a
preset checks that the user can read that preset" holds through the filtered list: a user can only
load presets they own (or, with `preset:read:all`, any, which is what the permission is for; the
New Analysis page still asks without `allUsers`, so even an admin loads their own there). No
`presetId` is added to the start request (see Out of scope).

### Frontend

- `PERMISSIONS.presetReadAll = 'preset:read:all'`.
- `settingsApi.listPresets({ allUsers })` sets `allUsers=true` only when asked; New Analysis calls it
  without.
- `PresetManager.vue`: for a user with `preset:read:all`, an **All users** switch above the table,
  off by default; when on, the list asks for everyone's presets and an **Owner** column shows the
  username (`—` when there is none). `presetColumns.ts` takes the switch into its column plan
  (`keys` gets `owner`), covered by `presetColumns.spec.ts`. Users without the permission see
  neither. The note under the heading says the list holds your own presets.
- A VIEWER now sees an empty preset list on Settings and New Analysis (they own none); the existing
  "No presets yet." empty state covers it.
- Translations (`en.json`, `tr.json`): `presets.allUsers`, `presets.owner`; `presets.note` reworded.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Owner in the settings domain

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/settings/core/src/main/java/.../settings/core/model/`: `OwnerId.java`, `PresetAccess.java` (new); `Preset.java`
  - `.../settings/core/inbound/`: `ManagePresetsUseCase.java`, `AssignUnownedPresetsUseCase.java` (new)
  - `.../settings/core/outbound/persistence/PresetRepositoryPort.java`
  - `.../settings/core/service/PresetService.java`
  - `artifact/backend/domain/settings/adapter/src/main/java/.../settings/adapter/persistence/`: `JpaPresetRepositoryAdapter.java`, `PresetJpaRepository.java`, `entity/PresetEntity.java`, `entity/OwnerIdAttributeConverter.java` (new), `mapper/PresetToPresetEntityMapper.java`, `mapper/PresetEntityToPresetMapper.java`
  - `artifact/backend/domain/settings/adapter/src/main/resources/.../persistence/migration/V4__add_owner.sql` (new)
- **Steps**:
  - [x] The model types and the owner on `Preset`.
  - [x] The new use case signatures and the `canSee` checks in `PresetService`, NOT_FOUND for a preset the caller cannot see; `assignUnowned`.
  - [x] Migration, entity column with its converter, mappers, the owner `Specification`, `assignOwnerWhereMissing`.
- **Tests**:
  - `PresetServiceTest`: a preset belongs to its creator; `listPresets` returns only the caller's own, with `allUsers` and the permission everyone's, and `allUsers` without the permission is ignored; `updatePreset` and `deletePreset` of another user's preset fail with NOT_FOUND and with the permission succeed; an update keeps the owner; `assignUnowned` touches only presets without an owner.
  - `JpaPresetRepositoryAdapterTest`: the owner is stored and read back, `findAll(owner)` filters, `save` of an update keeps it, `assignOwnerWhereMissing` sets only missing owners and raises their version.
  - `V4SettingsOwnerMigrationTest` (new, as `V2SettingsVersionMigrationTest`): a row written at V3 survives V4 with `OWNER_ID` null.
  - `SettingsPersistenceConventionsTest` passes unchanged (names, index, schema).

### WP2: Orchestration

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/orchestration/src/main/java/.../orchestration/ownership/inbound/AssignPresetOwnersUseCase.java` (new)
- **Steps**:
  - [x] The startup assignment on `ApplicationReadyEvent`.
- **Tests**: `AssignPresetOwnersUseCaseTest` (new, as the analysis one): assigns to the first admin; does nothing without one.

### WP3: BFF and the API

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/.../bff/delegate/impl/SettingsApiDelegateImpl.java`, `.../mapper/PresetToPresetDtoMapper.java`
  - `artifact/backend/bff/api/src/main/java/.../bff/controller/api/`: `SettingsApiController.java`, `SettingsApiDelegate.java` (`allUsers` parameter), `model/PresetDto.java`, `model/PresetOwnerDto.java` (new)
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated)
- **Steps**:
  - [x] The access per call, `allUsers`, the owner in the DTO.
  - [x] Regenerate `openapi.json` against a throwaway PostgreSQL and run `mise run api-types`.
- **Tests**:
  - `PresetOwnershipIntegrationTest` (new, `artifact/backend/src/test/...`, as `PresetsApiIntegrationTest` and `AnalysisOwnershipIntegrationTest`, with an ANALYST user A, an ANALYST user B and the admin): B's preset is missing from A's list (also with `allUsers=true`), and A gets **404** on `PUT` and `DELETE /api/presets/{id}` of B's preset; the admin lists both with `allUsers=true` (only their own without it), renames and deletes B's preset, and sees `owner` with B's username; a preset A creates has A as `owner`. A VIEWER lists nothing and gets 403 on create.
  - The same test seeds presets without an owner (through `PresetRepositoryPort`) and checks that `AssignPresetOwnersUseCase` gives them to the first admin.
  - `PresetsApiIntegrationTest` and `EndpointPermissionsIntegrationTest` still pass (the admin owns what it creates).

### WP4: Frontend

- **Status**: done
- **Depends on**: WP3
- **Files**:
  - `artifact/frontend/src/features/auth/model/permissions.ts`
  - `artifact/frontend/src/features/settings/api.ts`, `.../components/PresetManager.vue`, `.../model/presetColumns.ts`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [x] The permission key, the `allUsers` call, the Owner column and the All users switch.
- **Tests**: `presetColumns.spec.ts` (the Owner column appears only with the switch on); `permissions.spec.ts` covers the new key if it lists them; `features/settings/api.spec.ts` (new, client mocked): `allUsers` reaches the query only when set.

### WP5: e2e, screenshot and docs

- **Depends on**: WP4
- **Files**:
  - `e2e/tests/preset-ownership.e2e.ts` (new)
  - the screenshot script that produces `docs/screenshots/presets.png` (or a new `e2e/screenshots/presets.shot.ts` when none does), `docs/screenshots/presets.png`
  - `README.md`
- **Steps**:
  - [ ] e2e: the admin creates an ANALYST (unique name, through the API, as `ownership.e2e.ts`) and a preset of their own through the API; the analyst logs in, changes the one-time password, saves a preset on New Analysis, sees only that preset in Settings (no All users switch) and not the admin's in the Load a preset list. The admin turns on **All users** in Settings and sees both presets with the Owner column.
  - [ ] Retake the screenshot and update the docs below.
- **Tests**: the e2e test above (`mise run e2e`); the existing `settings.e2e.ts` keeps passing.

## Tests

- Unit: `PresetServiceTest` (the checks per use case), `AssignPresetOwnersUseCaseTest`.
- Adapter: `JpaPresetRepositoryAdapterTest`, `V4SettingsOwnerMigrationTest`,
  `SettingsPersistenceConventionsTest` (PostgreSQL through Testcontainers).
- Integration: `PresetOwnershipIntegrationTest` proves "user A can't see or change user B's
  presets, and an admin can" for every preset endpoint, and "existing presets belong to the first
  admin".
- Frontend: Vitest for the column plan and the API call; `vue-tsc` checks the generated `owner`
  field and the permission key (`mise run check`).
- e2e: `preset-ownership.e2e.ts` through the real UI.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Login" (the roles paragraph: "The presets are those of every user until the ownership issue (#92) makes "their own" true") | Presets are the user's own: the user who saved one owns it, another user's preset answers 404 unless the user has `preset:read:all` (ADMIN); `allUsers=true` on `GET /api/presets`; on the first start after the upgrade, presets without an owner are given to the first admin; a VIEWER, who cannot save presets, has none |
| Text | `README.md`, the New Analysis section ("Save as preset … Settings lists the presets") | Settings lists your own presets; with `preset:read:all` an **All users** switch shows everyone's, with an **Owner** column |
| Text | `artifact/frontend/openapi.json`, `shared/api/schema.d.ts` | regenerated (`owner`, `allUsers`) |
| Screenshot | `docs/screenshots/presets.png`, retaken | Settings → Presets as the admin with **All users** on and the Owner column, two presets of two users (README "New analysis", the "Presets in Settings" figure) |

`docs/event-protocol.md` and `docs/coding-convention/`: unchanged, the runner contract and the
conventions do not change.

## Out of scope

- **Shared presets** (an admin marks a preset visible to everyone, read-only): not in this plan. It
  is the same feature as sharing analyses (#192) and needs its own permission, a visibility column
  and UI. It is also what would give a VIEWER presets to load, since they own none. Follow-up: a
  new issue "Share presets with every user", to be opened when this plan is approved.
- A `presetId` on the start request: nothing on the backend reads a preset when an analysis starts,
  so there is nothing to check there. Add it together with any server-side use of a preset.
- Changing a preset's owner by hand, and filtering the list by one chosen owner.
- Preset name uniqueness (names are not unique today and stay so).

## Open questions

- **Shared presets**: the plan answers the issue's question with "no, follow-up" (see Out of scope).
  The cost is that a VIEWER, who cannot save presets, has an empty list. Confirm, or ask for the
  shared presets to be added to this plan.
- **Start from a preset**: the plan treats the issue's second acceptance criterion as met by the
  filtered list, since the start request has no preset reference. Confirm, or say what check you
  expect on the start endpoint.
