# Plan: Analyses belong to the user who started them

- **Issue**: #91 (Ownership: analyses belong to the user who started them)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every analysis has an owner: the user who started it (a rerun belongs to the user who reran it), and
the first admin for imported runs and for the analyses that existed before this change. A user
lists, reads, follows (event stream, log), stops and reruns only their own analyses, and gets **404**
for anyone else's, as for an analysis that does not exist. A user with `analysis:read:all` (ADMIN)
can act on every analysis, and the **Analyses** page gives them an **All users** switch and an
**Owner** column.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: analysis domain (core, persistence adapter) | `backend-java-package-structure.md`: value types in `core.model`, use cases in `core.inbound`, the port in `core.outbound.persistence`; `backend-java-persistence.md`: Spring Data JPA only (JPQL, no native query), the entity and mappers in `adapter.persistence.entity` / `mapper`, `saveAndFlush`, `@Version` stays; `backend-database-naming.md`: a new migration `V3__add_owner.sql`, uppercase quoted names, a named index, no foreign key to another domain's schema, the column commented |
| Backend: identity domain (core) | `backend-java-package-structure.md`: a new use case in `core.inbound`, its service in `core.service` (package-private) |
| Backend: orchestration | A new feature package `orchestration.ownership` with `inbound` and `service` only (`orchestration_features_follow_the_domain_core_shape`); the domains stay independent (`domains_are_independent`), so everything that needs both the analysis and the identity domain lives here |
| Backend: BFF | `backend-java-rest-api.md`: `200` endpoints return their DTO; the 404 comes from `AnalysisException(NOT_FOUND)` through `DomainExceptionHandler` and the existing `AnalysisExceptionToApiExceptionMapper`, never a `try` / `catch` in the delegate; `@RequiresPermission` stays on every delegate method (`TAP-A1`); Spring Security code (reading the `Authentication`) in `bff.delegate.impl.config` (`bff_api_holds_no_security_code`); a new DTO in `bff.controller.api.model` |
| Shared types | `repository-shared-types.md`: `openapi.json` regenerated, then `mise run api-types` for `shared/api/schema.d.ts`; the frontend uses the generated `Analysis` type and `Permission` key |
| Frontend | `frontend-folder-structure.md`: the backend call in `features/analysis/api.ts`, the column in `features/analysis/components/AnalysisTable.vue`, the switch in `pages/RunsPage.vue`, the permission key in `features/auth/model/permissions.ts` (`satisfies Record<string, Permission>`), translations in `shared/i18n/locales/*.json` |
| e2e | `e2e/tests/*.e2e.ts` style: the shared admin's session must not change; users created with unique names, as in `users.e2e.ts` |
| Docs | Root `README.md`, `docs/screenshots/analyses.png` |

## Design

### Owner in the analysis domain

The analysis domain does not know the identity domain, so it has its own plain ID of a user:

- `core.model.OwnerId(String value)`: the ID of the user an analysis belongs to (non-blank).
- `core.model.AnalysisAccess(OwnerId user, boolean allUsers)`: who is asking and whether they may see
  every analysis (`analysis:read:all`). One method, `boolean canSee(Analysis)`: `allUsers`, or the
  analysis's owner equals `user`.
- `Analysis` gets a component `OwnerId owner`, after `externalRef` / `runnerRef` and before `version`.
  It is `null` only on rows from before this change until the startup assignment below has run (and,
  on a fresh install, on runs imported before the first admin exists). Every transition carries it.
  `Analysis.queued(id, spec, owner, now)` takes it; `Analysis.imported(id, version, owner, external)`
  too.
- `AnalysisFilter` gets `OwnerId owner` (`null`: every owner). `AnalysisFilter.ALL` stays all owners.

### The checks live in the use cases

Every inbound port that hands out or acts on one analysis takes the `AnalysisAccess` and answers an
analysis the caller cannot see with the same `AnalysisException(NOT_FOUND, "Analysis not found: <id>")`
as a missing one, so its existence is not revealed (the BFF answers **404** `analysis_not_found` as
today):

| Use case (`core.inbound`) | New signature | Rule |
|---|---|---|
| `StartAnalysisUseCase` | `start(AnalysisSpec spec, OwnerId owner)` | the new analysis belongs to `owner` |
| `ListAnalysesUseCase` | `list(AnalysisFilter filter, AnalysisAccess access)` | without `allUsers`, the filter's owner is forced to `access.user()`; with it, the filter is used as given |
| `GetAnalysisUseCase` | `get(AnalysisId id, AnalysisAccess access)` | NOT_FOUND unless `canSee` |
| `StopAnalysisUseCase` | `stop(AnalysisId id, AnalysisAccess access)` | NOT_FOUND unless `canSee` |
| `RerunAnalysisUseCase` | `rerun(AnalysisId id, AnalysisAccess access)` | NOT_FOUND unless `canSee`; the new run belongs to `access.user()` |
| `SubscribeAnalysisEventsUseCase` | `subscribe(AnalysisId id, long afterSeq, AnalysisAccess access, RunEventListener listener)` | NOT_FOUND unless `canSee`, checked before any replay (the existing existence check in `AnalysisEventHub`) |
| `ReadAnalysisLogsUseCase` | `tailLogs(AnalysisId id, AnalysisAccess access, int maxLines)` | through `GetAnalysisUseCase.get(id, access)` |

The old signatures go away, so the compiler finds every caller and no unchecked entry point stays.
`AnalysisService` keeps its private `get(AnalysisId)` for its own lifecycle code (dispatch, sink,
recovery), which acts for the platform, not a user. The event stream answers the 404 as a normal
JSON error, as it does today for an unknown run: the emitter buffers the `open` comment until the
delegate returns, so an exception from `subscribe` still reaches `DomainExceptionHandler`.

Two more inbound ports, for the orchestration:

- `RegisterExternalAnalysisUseCase.register(ExternalAnalysis external, OwnerId owner)`: a new
  EXTERNAL record belongs to `owner` (may be `null`, see below); a refresh keeps the stored owner.
- `AssignUnownedAnalysesUseCase.assignUnowned(OwnerId owner)` (new, `core.inbound`, implemented by
  `AnalysisService`): gives every analysis without an owner to `owner`, returns how many.

### Persistence

- `V3__add_owner.sql`: `ALTER TABLE "ANALYSIS"."ANALYSES" ADD COLUMN "OWNER_ID" TEXT;` (nullable, see
  above; the comment says it holds an `"IDENTITY"."USERS"` ID with no foreign key, since no migration
  references another domain's schema) and `CREATE INDEX "ANALYSES_OWNER_ID_IDX" ON
  "ANALYSIS"."ANALYSES" ("OWNER_ID");`.
- `AnalysisEntity` gets `OWNER_ID` through an `OwnerIdAttributeConverter` in `entity`
  (`@Convert`, not `autoApply`), the same way as `Username` in identity. The MapStruct mappers
  (`AnalysisToAnalysisEntityMapper`, `AnalysisEntityToAnalysisMapper`) map it by name.
- `JpaAnalysisRepositoryAdapter.findAll` adds a `hasOwner(OwnerId)` `Specification` when the filter
  has an owner. `update` keeps the owner it loaded (an update never changes it); `replaceImported`
  keeps the stored owner as well.
- `AnalysisRepositoryPort.assignOwnerWhereMissing(OwnerId owner)` returns the number of rows: a
  `@Transactional` adapter method that loads the entities with `OWNER_ID IS NULL` (a derived finder),
  sets the owner and `saveAndFlush`es them, so each row's `VERSION` rises as on any other write. The
  number of rows is small (one per run), so no bulk JPQL update is needed.

### Identity: the first admin and usernames

A new use case `core.inbound.UserDirectoryUseCase`, implemented by a package-private
`core.service.UserDirectoryService` over `UserRepositoryPort`:

- `Optional<UserId> firstAdmin()`: the enabled user with the ADMIN role created first (`createdAt`,
  then the ID); empty when there is none yet.
- `Map<UserId, Username> usernames(Collection<UserId> ids)`: the usernames of the given users, for
  the Owner column; IDs without a user are left out.

### Orchestration: owners for imported and existing analyses

New feature package `orchestration.ownership`:

- `inbound.AssignAnalysisOwnersUseCase` with `void assignUnownedToFirstAdmin()`, implemented by
  `service.AnalysisOwnershipService`. It runs on `ApplicationReadyEvent` (after every
  `ContextRefreshedEvent` listener, so after `FirstAdminService` created the first admin and the
  startup import ran), asks `UserDirectoryUseCase.firstAdmin()` and, when there is one, calls
  `AssignUnownedAnalysesUseCase.assignUnowned`. It logs the count at INFO when it is above 0. This is
  what gives every analysis of an existing installation an owner on the first start after the
  upgrade.
- `ImportExistingRunsService` (startup, data dir watch, rescan) passes
  `UserDirectoryUseCase.firstAdmin()` (or `null` when there is none yet) to `register`: imported
  runs are not started by a platform user, so they belong to the first admin, whoever pressed
  **Scan data dir**.
- `AnalysisReportService`: `getReport(AnalysisId, AnalysisAccess)` and
  `getPrices(AnalysisId, AnalysisAccess)` (`GetAnalysisReportUseCase`, `GetAnalysisPricesUseCase`),
  through `GetAnalysisUseCase.get(id, access)`. The report export (Markdown, HTML, PDF) is built in
  the browser from `GET /api/analyses/{id}/report`, so this covers it.

### BFF

- `bff.delegate.impl.config.CurrentUser` (`@Component`): `UserId id()` from the
  `Authentication`'s principal (the user ID string, as `UsersApiDelegateImpl#currentUserId` reads it
  today) and `boolean has(Permission)` from its authorities. `UsersApiDelegateImpl` keeps its own
  helper (out of scope).
- `AnalysesApiDelegateImpl` builds the access once per call:
  `new AnalysisAccess(new OwnerId(currentUser.id().value()), currentUser.has(Permission.ANALYSIS_READ_ALL))`,
  and passes it to every use case above; `startAnalysis` passes the owner. The permissions on the
  methods stay as they are.
- `GET /api/analyses` gets the query parameter `allUsers` (boolean, default `false`): with `true`
  and `analysis:read:all`, every user's analyses; without the permission it is ignored (own
  analyses only, no error). Still `200` with `List<AnalysisDto>`.
- `AnalysisDto` gets `owner` (`AnalysisOwnerDto { id, username }`, `null` for an analysis without an
  owner). The delegate looks the usernames up once per response with
  `UserDirectoryUseCase.usernames` (the IDs of the list, or the one ID of a single analysis) and maps
  them with `AnalysisToAnalysisDtoMapper` (a second source parameter or an `@AfterMapping` context,
  whichever MapStruct needs; one `map` method stays the rule). Every endpoint returning an
  `AnalysisDto` (list, get, start, stop, rerun) fills it.
- No new error: 404 is the existing `NOT_FOUND` mapping. `getReport` / `getPrices` keep their
  `report_not_found` / `prices_not_found` `ApiException` for an analysis the caller can see but
  without files.

### Frontend

- `PERMISSIONS.analysisReadAll = 'analysis:read:all'`.
- `analysisApi.listAnalyses({ status, ticker, allUsers })` sets `allUsers=true` only when asked.
- `AnalysisTable.vue`: a prop `showOwner` adds an **Owner** column (the owner's username, `—` when
  there is none).
- `RunsPage.vue`: for a user with `analysis:read:all`, an **All users** switch next to the filters,
  off by default, kept in the route query (`?allUsers=true`) so a reload keeps it; when on, the list
  asks for every user's analyses and shows the Owner column. Users without the permission see
  neither.
- The Dashboard and the other pages call the API as before and so show the user's own analyses; a
  link to another user's run (compare, run detail) shows the existing "not found" state.
- Translations (`en.json`, `tr.json`): `runs.allUsers`, `runs.owner`.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Owner in the analysis domain

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/analysis/core/src/main/java/.../domain/analysis/core/model/`: `OwnerId.java`, `AnalysisAccess.java` (new); `Analysis.java`, `AnalysisFilter.java`
  - `.../analysis/core/inbound/`: `StartAnalysisUseCase.java`, `ListAnalysesUseCase.java`, `GetAnalysisUseCase.java`, `StopAnalysisUseCase.java`, `RerunAnalysisUseCase.java`, `SubscribeAnalysisEventsUseCase.java`, `ReadAnalysisLogsUseCase.java`, `RegisterExternalAnalysisUseCase.java`, `AssignUnownedAnalysesUseCase.java` (new)
  - `.../analysis/core/outbound/persistence/AnalysisRepositoryPort.java`
  - `.../analysis/core/service/`: `AnalysisService.java`, `AnalysisEventHub.java`, `AnalysisLogService.java`, `ExternalAnalysisService.java`
  - `artifact/backend/domain/analysis/adapter/src/main/java/.../analysis/adapter/persistence/`: `JpaAnalysisRepositoryAdapter.java`, `AnalysisJpaRepository.java`, `entity/AnalysisEntity.java`, `entity/OwnerIdAttributeConverter.java` (new), the two analysis mappers in `mapper/`
  - `artifact/backend/domain/analysis/adapter/src/main/resources/.../persistence/migration/V3__add_owner.sql` (new)
- **Steps**:
  - [x] The model types, the owner on `Analysis` and `AnalysisFilter`.
  - [x] The new use case signatures and the `canSee` checks in the services, NOT_FOUND for an analysis the caller cannot see.
  - [x] `assignUnowned` in `AnalysisService`; the owner passed to `register` in `ExternalAnalysisService`, kept on a refresh.
  - [x] Migration, entity column with its converter, mappers, the owner `Specification`, `assignOwnerWhereMissing`.
- **Tests**:
  - `AnalysisServiceTest`: a run belongs to its starter; `list` without `allUsers` returns only the caller's runs, with it everyone's; `get`, `stop`, `rerun` of another user's run fail with NOT_FOUND and with `allUsers` succeed; a rerun belongs to the rerunning user; `assignUnowned` touches only analyses without an owner.
  - `AnalysisEventHubTest`: subscribing to another user's run fails with NOT_FOUND before any event is delivered; with `allUsers` it replays.
  - `AnalysisLogServiceTest`: another user's log fails with NOT_FOUND.
  - `ExternalAnalysisServiceTest`: a new import gets the given owner, a refresh keeps the stored one.
  - `JpaAnalysisRepositoryAdapterTest`: the owner is stored and read back, `findAll` filters by owner, `update` / `replaceImported` keep it, `assignOwnerWhereMissing` sets only missing owners and raises their version.
  - `V3AnalysisOwnerMigrationTest` (new, as `V2AnalysisVersionMigrationTest`): a row written at V2 survives V3 with `OWNER_ID` null.
  - `AnalysisPersistenceConventionsTest` passes unchanged (names, index, schema).

### WP2: First admin and usernames in identity

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/.../identity/core/inbound/UserDirectoryUseCase.java` (new)
  - `.../identity/core/service/UserDirectoryService.java` (new)
- **Steps**:
  - [x] `firstAdmin()` and `usernames(...)` over `UserRepositoryPort.findAll()` / `findById`.
- **Tests**: `UserDirectoryServiceTest` (new): the earliest enabled ADMIN is the first admin, a disabled or non-admin user is not, none gives empty; `usernames` returns the known users and leaves out unknown IDs.

### WP3: Orchestration

- **Status**: done
- **Depends on**: WP1, WP2
- **Files**:
  - `artifact/backend/orchestration/src/main/java/.../orchestration/ownership/inbound/AssignAnalysisOwnersUseCase.java`, `.../ownership/service/AnalysisOwnershipService.java` (new)
  - `.../orchestration/report/inbound/GetAnalysisReportUseCase.java`, `GetAnalysisPricesUseCase.java`, `.../report/service/AnalysisReportService.java`, `.../report/service/ImportExistingRunsService.java`
- **Steps**:
  - [x] The startup assignment on `ApplicationReadyEvent`.
  - [x] Imports owned by the first admin.
  - [x] Report and prices through the access check.
- **Tests**:
  - `AnalysisOwnershipServiceTest` (new): assigns to the first admin; does nothing without one.
  - `ImportExistingRunsServiceTest`: imports are registered with the first admin as owner, and with `null` before one exists.
  - `AnalysisReportServiceTest` (new): another user's report and prices fail with NOT_FOUND.

### WP4: BFF and the API

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/.../bff/delegate/impl/config/CurrentUser.java` (new)
  - `.../bff/delegate/impl/AnalysesApiDelegateImpl.java`, `.../mapper/AnalysisToAnalysisDtoMapper.java`
  - `artifact/backend/bff/api/src/main/java/.../bff/controller/api/`: the analyses controller and delegate interface (`allUsers` parameter), `model/AnalysisDto.java`, `model/AnalysisOwnerDto.java` (new)
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated)
- **Steps**:
  - [x] `CurrentUser`, the access per call, `allUsers`, the owner in the DTO.
  - [x] Regenerate `openapi.json` and `mise run api-types`.
- **Tests**:
  - `AnalysisOwnershipIntegrationTest` (new, `artifact/backend/src/test/...`, as `AnalysesApiIntegrationTest`, with an ANALYST user A, an ANALYST user B and the admin): B's run is missing from A's list (also with `allUsers=true`), and A gets **404** on B's `GET /api/analyses/{id}`, `…/report`, `…/prices`, `…/logs`, `…/events`, `POST …/stop` and `…/rerun`; the admin lists both with `allUsers=true` (only their own without it), reads B's run, report, log and stream, and sees `owner` with B's username; a run A starts has A as `owner`.
  - The same test seeds analyses without an owner (through the repository, before the application is ready, or by calling `AssignAnalysisOwnersUseCase` after inserting them) and checks that every analysis then belongs to the first admin.
  - `AnalysesApiIntegrationTest` and `EndpointPermissionsIntegrationTest` still pass (the admin owns what it starts).

### WP5: Frontend

- **Status**: done
- **Depends on**: WP4
- **Files**:
  - `artifact/frontend/src/features/auth/model/permissions.ts`
  - `artifact/frontend/src/features/analysis/api.ts`, `.../components/AnalysisTable.vue`
  - `artifact/frontend/src/pages/RunsPage.vue`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [x] The permission key, the `allUsers` call, the Owner column, the All users switch in the route query.
- **Tests**: `features/analysis/api.spec.ts` (new, the client mocked): `allUsers` reaches the query only when set; `permissions.spec.ts` covers the new key if it lists them.

### WP6: e2e, screenshot and docs

- **Depends on**: WP5
- **Files**:
  - `e2e/tests/ownership.e2e.ts` (new)
  - `e2e/screenshots/analyses.shot.ts`, `docs/screenshots/analyses.png`
  - `README.md`
- **Steps**:
  - [ ] e2e: the admin creates an ANALYST (unique name, as in `users.e2e.ts`, through the API); the analyst logs in, changes the one-time password, and sees an empty **Analyses** list without the All users switch while the admin's completed run (`completedAnalysis`) exists; opening that run's URL shows "not found". The admin turns on **All users** and sees the run with the Owner column.
  - [ ] Retake the screenshot and update the docs below.
- **Tests**: the e2e test above (`mise run e2e`).

## Tests

- Unit: the analysis core services (the checks per use case), the identity directory, the
  orchestration services.
- Adapter: `JpaAnalysisRepositoryAdapterTest`, `V3AnalysisOwnerMigrationTest`,
  `AnalysisPersistenceConventionsTest` (PostgreSQL through Testcontainers).
- Integration: `AnalysisOwnershipIntegrationTest` proves the issue's "user A can't list, read,
  export, stream or delete user B's analysis, and an admin can" for every analysis endpoint that
  exists (there is no delete endpoint, see Out of scope; export reads the report endpoint), and "after
  the migration on existing data, every analysis has an owner".
- Frontend: Vitest for the API call; `vue-tsc` checks the generated `owner` field and the permission
  key (`mise run check`).
- e2e: `ownership.e2e.ts` through the real UI.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "7. Past analyses" | **Analyses** lists your own runs; with `analysis:read:all` (ADMIN) an **All users** switch shows everyone's, with an **Owner** column. Imported runs belong to the first admin |
| Text | `README.md`, "Login" (the roles paragraph, the event stream line, the permission table's `GET /api/analyses…` rows) | Analyses are now the user's own: drop "until the ownership issues (#91, #92)" for analyses (presets stay with #92); another user's analysis answers 404 unless `analysis:read:all`; `allUsers=true` on `GET /api/analyses`; on the first start after the upgrade, analyses without an owner are given to the first admin |
| Text | `artifact/frontend/openapi.json`, `shared/api/schema.d.ts` | regenerated (`owner`, `allUsers`) |
| Screenshot | `docs/screenshots/analyses.png`, retaken | The Analyses list as the admin, with the All users switch on and the Owner column, the same four replayed runs, two ticked for comparing (README "7. Past analyses") |

`docs/event-protocol.md`: unchanged, the runner ↔ backend contract does not change. The coding
convention documents: unchanged, nothing new in the conventions (the new orchestration feature follows
the existing shape).

## Out of scope

- **Deleting analyses**: there is no delete endpoint yet (`analysis:delete` is granted but used by
  no endpoint), and no "delete all" permission. The endpoint gets the same check
  (`AnalysisAccess`, 404 for another user's analysis) when it is added, in its own issue.
- Preset ownership: #92.
- Changing an analysis's owner by hand, and filtering the list by one chosen owner (only "mine" or
  "all users").
- Moving `UsersApiDelegateImpl#currentUserId` onto `CurrentUser`.

## Decisions from the plan review

The open questions of the plan, answered when it was approved:

- **Imported runs** belong to the first admin, also when an ANALYST presses **Scan data dir**: no
  platform user started them, the data dir is shared, and the watch imports without any user.
- **All users** is off by default, so an admin sees their own runs until they turn it on; the choice
  is kept in the URL.
- **Owner usernames** are looked up in the BFF: `AnalysesApiDelegateImpl` calls the identity
  `UserDirectoryUseCase` next to the analysis use cases, with no orchestration use case that joins
  analyses with usernames.
