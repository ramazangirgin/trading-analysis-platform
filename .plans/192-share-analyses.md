# Plan: Share analyses with every user

- **Issue**: #192 (Share analyses: public analyses every user can see, with an analysis:share permission)
- **Plan**: 1 of 1 for this issue; depends on: none (#91 ownership, #89 user management, #90 custom
  roles and #181 the domain exception handler are merged)
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md): the
  `allUsers` query parameter of `GET /api/analyses` is replaced by `scope`, a breaking REST change

## Goal

The owner of a completed analysis who holds the new permission `analysis:share` can make it
**public**, so every user with `analysis:read` sees it, read-only, under **Shared with everyone** on
the Analyses page, and can make it **private** again. ADMIN holds `analysis:share`; a new built-in
role **PUBLISHER** holds only it, so an admin can let any user publish by adding that role on
Admin → Users. A public analysis can be opened, read, compared and exported by everyone, but only
its owner (or an admin with `analysis:read:all`) can stop it, run it again, read its log or change
it. An admin can make any public analysis private again.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: identity domain (core, persistence adapter) | `repository-shared-types.md` "Adding a value": the constant on `Permission` (last, since the database enum appends), `PermissionDto`, one migration that adds the enum value and a second one that grants it, as V7 / V8; `backend-database-naming.md`: uppercase quoted names; the new role has a fixed ID like ADMIN, ANALYST, VIEWER (`RoleId` constants) |
| Backend: analysis domain (core, persistence adapter) | `backend-java-package-structure.md`: `Visibility` and `AnalysisScope` in `core.model`, the new use case in `core.inbound`, its service in `core.service` (package-private), error codes on `AnalysisError`; `backend-java-persistence.md`: a PostgreSQL enum type for `Visibility` (never text), `VISIBILITY_CHANGED_AT` a domain fact set from the `Clock`, a managed-entity partial update with the version compared first and `saveAndFlush`, list filters as `Specification`s; `backend-database-naming.md`: migration `V4__add_visibility.sql`, enum type `"ANALYSIS"."VISIBILITY"`, index `ANALYSES_VISIBILITY_IDX` |
| Backend: BFF | `backend-java-rest-api.md`: the new `PUT` answers only `200` and returns `AnalysisDto`, no `ResponseEntity`; its errors are `AnalysisException`s answered by `DomainExceptionHandler` through `AnalysisExceptionToApiExceptionMapper`, no `try` / `catch` in the delegate; `@RequiresPermission` on the new delegate method (`TAP-A1`); DTOs and the request in `bff.controller.api.model`, mappers in `bff.delegate.impl.mapper` (`SourceToTargetMapper`, one `map` method) |
| Shared types | `repository-shared-types.md`: `openapi.json` regenerated, `mise run api-types` for `shared/api/schema.d.ts`; `Visibility`, `AnalysisScope` and the new `Permission` key reach the frontend through the generated types |
| Frontend | `frontend-folder-structure.md`: the calls in `features/analysis/api.ts`; the visibility rules the page needs as pure logic in `features/analysis/model/` with a spec; components in `features/analysis/components/`; page state in `pages/RunsPage.vue` and `pages/RunDetailPage.vue`; the permission in `features/auth/model/permissions.ts` and `features/roles/model/permissionGroups.ts`; translations in `shared/i18n/locales/en.json`, `tr.json` |
| e2e | `e2e/tests/*.e2e.ts`: the shared admin's session must not change, users created with unique names through the API (as `ownership.e2e.ts`); screenshots in `e2e/screenshots/*.shot.ts` |
| Docs | Root `README.md`, `docs/coding-convention/backend-java-persistence.md` (the timestamps table), `docs/screenshots/` |

## Design

### Identity: `analysis:share` and the PUBLISHER role

- `Permission.ANALYSIS_SHARE("analysis:share")`, after `ANALYSIS_OVERRIDE_MODELS`;
  `PermissionDto.ANALYSIS_SHARE("analysis:share")`.
- `RoleId.PUBLISHER = new RoleId("PUBLISHER")`, seeded by the migration below.
- `V9__add_analysis_share_permission.sql`: `ALTER TYPE "IDENTITY"."PERMISSION" ADD VALUE 'ANALYSIS_SHARE';`
- `V10__grant_analysis_share_and_seed_publisher_role.sql` (a migration of its own, as V8: a
  transaction cannot use an enum value it added): append `ANALYSIS_SHARE` to ADMIN's permissions,
  and insert the built-in role `PUBLISHER` (ID and name `PUBLISHER`, `BUILT_IN` true, description
  `Makes their own completed analyses public`, permissions `ARRAY['ANALYSIS_SHARE']`). ANALYST and
  VIEWER do not get it.
- PUBLISHER is a built-in role like ANALYST and VIEWER: its name and description are fixed, it
  cannot be deleted, its permissions can be changed. `RoleManagementService` needs no change (it
  treats `builtIn` and ADMIN, not the other IDs). A user keeps their other roles (`ANALYST` +
  `PUBLISHER`). As with every change to a user's own roles, it applies at the user's next login.

### Analysis domain: visibility

- `core.model.Visibility` (`PRIVATE`, `PUBLIC`).
- `Analysis` gets `Visibility visibility` (non-null) and `Instant visibilityChangedAt` (`null` until
  the first change), after `owner` and before `version`. `queued` and `imported` start `PRIVATE`;
  every transition carries both. A new `withVisibility(Visibility visibility, Instant now)`.
- `AnalysisAccess` gets a third component, `boolean share` (the caller holds `analysis:share`):
  `AnalysisAccess(OwnerId user, boolean allUsers, boolean share)`, and these rules, the one place
  they are written:
  - `canSee(analysis)`: `allUsers`, the owner, **or the analysis is `PUBLIC`**;
  - `canManage(analysis)`: `allUsers` or the owner (stop, rerun, log);
  - `canMakePublic(analysis)`: the owner and `share` (the status is checked by the use case, so the
    UI can show the switch disabled with a reason);
  - `canMakePrivate(analysis)`: `PUBLIC`, and the owner (with or without `share`) or `allUsers &&
    share`;
  - `canChangeVisibility(analysis)`: `PRIVATE ? canMakePublic : canMakePrivate`.
- `AnalysisFilter` gets `Visibility visibility` and `OwnerId notOwner` (`null`: no criterion).
  `AnalysisFilter.ALL` stays everything.
- `core.model.AnalysisScope` (`MINE`, `SHARED`, `ALL`). `ListAnalysesUseCase.list(AnalysisFilter
  filter, AnalysisScope scope, AnalysisAccess access)`:
  - `MINE`: the filter with `owner = access.user()`;
  - `SHARED`: the filter with `visibility = PUBLIC` and `notOwner = access.user()`;
  - `ALL`: the filter as given when `access.allUsers()`; without it, as `MINE` (no error, as
    `allUsers=true` is ignored today).

  Status and ticker work on every scope.
- Read and act checks in the existing services:

  | Use case | Check | Change |
  |---|---|---|
  | `GetAnalysisUseCase.get` | `canSee` | a public analysis is readable |
  | `SubscribeAnalysisEventsUseCase.subscribe` (`AnalysisEventHub`) | `canSee` | a public analysis is always completed, so this is the replay of a finished run |
  | `GetAnalysisReportUseCase`, `GetAnalysisPricesUseCase` (orchestration, through `get`) | `canSee` | none in code; the export is built in the browser from the report |
  | `StopAnalysisUseCase.stop`, `RerunAnalysisUseCase.rerun` | `canManage` | was `canSee` |
  | `ReadAnalysisLogsUseCase.tailLogs` (`AnalysisLogService`) | `canManage` | was `canSee` through `get`; the log can hold internal details |

  An analysis the caller may not see or manage answers `NOT_FOUND` as today (404), so a
  non-owner's stop, rerun or log of a public analysis is a 404 as well.
- New `core.inbound.ChangeAnalysisVisibilityUseCase` with
  `Analysis change(AnalysisId id, Visibility visibility, AnalysisAccess access, Long version)`,
  implemented by a new package-private `core.service.AnalysisVisibilityService` (repository port,
  `Clock`), so `AnalysisService` does not grow. In order:
  1. No analysis, or not `canSee`: `NOT_FOUND`.
  2. `version` differs from the stored one: `CONCURRENT_UPDATE` (nothing changes).
  3. The visibility is already the requested one: the analysis is returned unchanged, no write.
  4. `PUBLIC`: not the owner: `NOT_FOUND` (an admin cannot publish someone else's analysis); the
     owner without `share`: `SHARE_FORBIDDEN`; not `COMPLETED`: `NOT_SHAREABLE` (param `status`).
  5. `PRIVATE`: not `canMakePrivate`: `NOT_FOUND`.
  6. `repository.updateVisibility(analysis.withVisibility(visibility, clock.instant()))`; an
     `OptimisticLockingFailureException` becomes `CONCURRENT_UPDATE`, as `AnalysisService` does.
- `AnalysisError` gets `NOT_SHAREABLE` and `SHARE_FORBIDDEN`.
- Rerunning a public analysis is the owner's only, and the new run is private.

### Persistence

- `V4__add_visibility.sql`:
  `CREATE TYPE "ANALYSIS"."VISIBILITY" AS ENUM ('PRIVATE', 'PUBLIC');`,
  `"VISIBILITY" "ANALYSIS"."VISIBILITY" NOT NULL DEFAULT 'PRIVATE'` and
  `"VISIBILITY_CHANGED_AT" TIMESTAMPTZ` on `"ANALYSIS"."ANALYSES"`, and
  `CREATE INDEX "ANALYSES_VISIBILITY_IDX" ON "ANALYSIS"."ANALYSES" ("VISIBILITY");`.
  Existing analyses stay private.
- `AnalysisEntity`: `visibility` mapped as the other enums (`@Enumerated(EnumType.STRING)`,
  `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, `columnDefinition = "\"ANALYSIS\".\"VISIBILITY\""`) and
  `visibilityChangedAt`; the two mappers map them by name. `applyRunState` and `replaceImported`
  keep both (like the owner), so a run update or a re-import never changes the visibility. A new
  `applyVisibility(AnalysisEntity other)` copies only the two.
- `AnalysisRepositoryPort.updateVisibility(Analysis analysis)`: writes the visibility and its time
  and returns the stored record with its new version; in `JpaAnalysisRepositoryAdapter` the same
  load, `requireVersion`, copy and `saveAndFlush` as `update`.
- `findAll`: `hasVisibility` and `notOwnedBy` `Specification`s next to `hasOwner`.
- `VISIBILITY_CHANGED_AT` is a domain fact (the core sets it from the `Clock` when the owner shares
  or withdraws), not an audit field.

### BFF and the API

- `PUT /api/analyses/{id}/visibility` with `ChangeVisibilityRequest { @NotNull VisibilityDto
  visibility, @NotNull Long version }`: `200` with the `AnalysisDto`. `@RequiresPermission(ANALYSIS_READ)`;
  the use case checks ownership and `analysis:share`. Errors, all through `DomainExceptionHandler`
  and `AnalysisExceptionToApiExceptionMapper`:

  | `AnalysisError` | Status | Code |
  |---|---|---|
  | `NOT_FOUND` | 404 | `not_found` (as today) |
  | `SHARE_FORBIDDEN` | 403 | `analysis_share_forbidden` (prefixed like `analysis_override_forbidden`, since `forbidden` is a missing endpoint permission) |
  | `NOT_SHAREABLE` | 409 | `analysis_not_shareable`, `params.status` |
  | `CONCURRENT_UPDATE` | 409 | `concurrent_update` (as today) |

  A missing field or an unknown visibility is the existing 400 of `ApiExceptionHandler`.
- `GET /api/analyses?scope=MINE|SHARED|ALL` (`AnalysisScopeDto`, default `MINE`, upper case like
  `status`) replaces `allUsers`. Still `200` with `List<AnalysisDto>`.
- `AnalysisDto` gets `visibility` (`VisibilityDto`), `visibilityChangedAt`, `version` (the request
  needs it) and two flags computed from the caller's access, so the UI does not repeat the rules:
  `canManage` (stop, rerun, log) and `canChangeVisibility`. `owner` is there since #91.
- `AnalysesApiDelegateImpl`: `access()` adds `currentUser.has(Permission.ANALYSIS_SHARE)`; the
  list passes the mapped scope; `AnalysisToAnalysisDtoMapper` takes the access as a second
  `@Context` next to the usernames and fills the flags from `canManage` / `canChangeVisibility`.
  New mappers: `VisibilityDtoToVisibilityMapper`, `AnalysisScopeDtoToAnalysisScopeMapper`
  (MapStruct enum mappings; the DTO direction of `Visibility` goes through
  `VisibilityToVisibilityDtoMapper` used by the analysis mapper).
- `openapi.json` and `shared/api/schema.d.ts` regenerated.

### Frontend

- `PERMISSIONS.analysisShare = 'analysis:share'`; `'analysis:share'` in `ALL_PERMISSIONS`
  (`permissionGroups.ts`, area `analysis`); its label in `roles.permissions` of both locales.
- `shared/api/types.ts` exports `Visibility` and `AnalysisScope`.
- `analysisApi.listAnalyses({ status, ticker, scope })` sends `scope` only when it is not `MINE`;
  `analysisApi.changeVisibility(id, { visibility, version })`.
- `features/analysis/model/visibility.ts` (pure, with `visibility.spec.ts`):
  `visibilityControl(analysis, userId)` returns what the detail page shows: `switch` for the owner
  with `canChangeVisibility` (with `disabled: true` and the reason while a private analysis is not
  `COMPLETED`), `makePrivate` for a non-owner with `canChangeVisibility`, or `none`.
- `RunsPage.vue`: tabs **My analyses**, **Shared with everyone** and, with `analysis:read:all`,
  **All users**, in place of the All users switch; the tab is kept in the route query
  (`?scope=SHARED`, `?scope=ALL`; none for `MINE`) so a reload keeps it. The Owner column shows on
  the shared and all tabs. The empty state of the shared tab says that nobody has shared an analysis
  yet.
- `AnalysisTable.vue`: a **Public** tag next to the ticker of a public analysis.
- `RunDetailPage.vue`:
  - the visibility control in the header: the owner's **Public** switch, disabled with a tooltip
    ("Only a completed analysis can be made public") while not completed, and an `NPopconfirm`
    "Everyone on the platform will see this analysis." before it turns public (turning private
    needs no confirmation); an admin's **Make private** button, with a confirmation, on another
    user's public analysis;
  - another user's analysis shows "Shared by <username>" and no Stop, Run again or Logs tab
    (`canManage` false);
  - after a change, the page shows the returned analysis; a `concurrent_update` reloads it and
    says so.
- Translations (`en.json`, `tr.json`): the tabs, the tag, the switch, the confirmation, the tooltip,
  "Shared by", the shared tab's empty state, and the error codes `analysis_share_forbidden` and
  `analysis_not_shareable`.
- The Dashboard and Compare pages need no change: the Dashboard lists the user's own analyses
  (`MINE`), Compare reads each analysis by ID, which works for public ones.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `analysis:share` and the PUBLISHER role

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/.../identity/core/model/Permission.java`, `RoleId.java`
  - `artifact/backend/domain/identity/adapter/src/main/resources/.../identity/adapter/persistence/migration/V9__add_analysis_share_permission.sql`, `V10__grant_analysis_share_and_seed_publisher_role.sql` (new)
  - `artifact/backend/bff/api/src/main/java/.../bff/controller/api/model/PermissionDto.java`
  - tests named below
- **Steps**:
  - [x] The constant, the DTO constant, the role ID, the two migrations.
- **Tests**:
  - `JpaIdentityRepositoryTest`: the database enum still mirrors `Permission`; after the migrations
    ADMIN holds `ANALYSIS_SHARE`, PUBLISHER is built in with exactly it, ANALYST and VIEWER do not
    have it.
  - `RolePermissionsIntegrationTest`: ADMIN's `me` returns every `Permission::key`, `analysis:share`
    included; a user with ANALYST + PUBLISHER holds `analysis:share` after logging in.
  - `RolesApiIntegrationTest`: PUBLISHER is listed as built in, cannot be renamed or deleted
    (409 `built_in_role`).

### WP2: Visibility in the analysis domain

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/analysis/core/src/main/java/.../analysis/core/model/`: `Visibility.java`, `AnalysisScope.java` (new); `Analysis.java`, `AnalysisAccess.java`, `AnalysisFilter.java`
  - `.../analysis/core/exception/AnalysisError.java`
  - `.../analysis/core/inbound/`: `ChangeAnalysisVisibilityUseCase.java` (new), `ListAnalysesUseCase.java`
  - `.../analysis/core/outbound/persistence/AnalysisRepositoryPort.java`
  - `.../analysis/core/service/`: `AnalysisVisibilityService.java` (new), `AnalysisListService.java` (new, `ListAnalysesUseCase` moved out of `AnalysisService` so it does not grow), `AnalysisService.java`, `AnalysisLogService.java`
  - `artifact/backend/domain/analysis/adapter/src/main/java/.../analysis/adapter/persistence/`: `JpaAnalysisRepositoryAdapter.java`, `entity/AnalysisEntity.java`, the two analysis mappers in `mapper/`
  - `artifact/backend/domain/analysis/adapter/src/main/resources/.../persistence/migration/V4__add_visibility.sql` (new)
  - every existing caller and test of `AnalysisAccess`, `AnalysisFilter` and `list` (the compiler names them; `orchestration` included)
- **Steps**:
  - [x] The model types, the access rules, the filter fields and the scopes.
  - [x] `AnalysisVisibilityService` with the rules in the order above; `canManage` for stop, rerun
    and the log.
  - [x] Migration, entity columns, mappers, `updateVisibility`, the two `Specification`s; run
    updates and re-imports keep the visibility.
- **Tests**:
  - `AnalysisAccessTest` (new): each rule of `canSee`, `canManage`, `canMakePublic`,
    `canMakePrivate`, `canChangeVisibility` for owner, other user, admin, with and without `share`,
    private and public.
  - `AnalysisVisibilityServiceTest` (new): the owner with `share` makes a completed analysis public
    (time from the clock) and private again; a running, queued, failed or stopped one fails with
    `NOT_SHAREABLE`; the owner without `share` gets `SHARE_FORBIDDEN` for public and can still make
    it private; another user gets `NOT_FOUND` for a private and for a public analysis, an admin
    without `share` too; an admin with `share` makes another user's public analysis private but
    cannot make another user's analysis public; a stale version fails with `CONCURRENT_UPDATE` and
    nothing is written; the same visibility writes nothing; an optimistic lock failure from the port
    becomes `CONCURRENT_UPDATE`.
  - `AnalysisServiceTest` (`list` through `AnalysisListService`): `list` per scope (`SHARED` holds other users' public analyses only,
    never one's own or a private one; `ALL` without `allUsers` is `MINE`; status and ticker apply to
    every scope); another user's public analysis can be read but `stop` and `rerun` fail with
    `NOT_FOUND`; a queued run is private.
  - `AnalysisLogServiceTest`: another user's public analysis's log fails with `NOT_FOUND`; the
    owner and an admin read it.
  - `AnalysisEventHubTest`: another user's public analysis replays; a private one fails with
    `NOT_FOUND`.
  - `JpaAnalysisRepositoryAdapterTest`: visibility and its time are stored and read back;
    `updateVisibility` raises the version and a stale copy fails with
    `OptimisticLockingFailureException`, leaving the row unchanged; `update` and `replaceImported`
    keep the visibility; `findAll` by visibility and by "not owned by".
  - `V4AnalysisVisibilityMigrationTest` (new, as `V3AnalysisOwnerMigrationTest`): a row written at
    V3 is `PRIVATE` with no change time after V4.
  - `AnalysisPersistenceConventionsTest` passes unchanged (names, enum type, index, schema).

### WP3: BFF and the API

- **Status**: done
- **Depends on**: WP1, WP2
- **Files**:
  - `artifact/backend/bff/api/src/main/java/.../bff/controller/api/`: `AnalysesApiController.java`, `AnalysesApiDelegate.java`; `model/`: `ChangeVisibilityRequest.java`, `VisibilityDto.java`, `AnalysisScopeDto.java` (new), `AnalysisDto.java`
  - `artifact/backend/bff/impl/src/main/java/.../bff/delegate/impl/`: `AnalysesApiDelegateImpl.java`; `mapper/`: `AnalysisToAnalysisDtoMapper.java`, `VisibilityDtoToVisibilityMapper.java`, `VisibilityToVisibilityDtoMapper.java`, `AnalysisScopeDtoToAnalysisScopeMapper.java` (new); `mapper/error/AnalysisExceptionToApiExceptionMapper.java`
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated, against a throwaway PostgreSQL)
- **Steps**:
  - [x] The endpoint, the request, the scope parameter in place of `allUsers`, the DTO fields and
    flags, the error codes.
  - [x] Regenerate `openapi.json` and run `mise run api-types`.
- **Tests**:
  - `AnalysisSharingIntegrationTest` (new, `artifact/backend/src/test/...`, as
    `AnalysisOwnershipIntegrationTest`; user A with ANALYST, user B with ANALYST, the admin):
    - A without PUBLISHER: `PUT …/visibility` `PUBLIC` answers 403 `analysis_share_forbidden`, and
      A's `AnalysisDto` has `canChangeVisibility` false; after PUBLISHER is added and A logs in
      again, it answers 200 with `visibility` `PUBLIC` and a new `version`;
    - a running or failed analysis answers 409 `analysis_not_shareable`;
    - B lists it under `scope=SHARED` with A's username, reads `GET /{id}`, `…/report`, `…/prices`
      and `…/events`, and gets 404 on `…/logs`, `POST …/stop`, `…/rerun` and on `PUT …/visibility`;
      B's `AnalysisDto` has `canManage` and `canChangeVisibility` false; A's own public analysis is
      not in A's `SHARED` list;
    - after A makes it private, B gets 404 on `GET /{id}` and no longer lists it;
    - A without PUBLISHER (removed, logged in again) can make a public analysis private but not
      public;
    - the admin makes A's public analysis private; the admin gets 404 making A's private analysis
      public;
    - a stale `version` answers 409 `concurrent_update` and the visibility is unchanged;
    - `scope=ALL` lists every user's analyses for the admin, only the own ones for A; a private
      analysis of A is seen by A and the admin only.
  - `AnalysisOwnershipIntegrationTest`, `AnalysesApiIntegrationTest`: `allUsers=true` becomes
    `scope=ALL`; otherwise they pass unchanged.
  - `EndpointPermissionsIntegrationTest`: the new endpoint needs `analysis:read` (403 without it).

### WP4: Frontend

- **Status**: done
- **Depends on**: WP3
- **Files**:
  - `artifact/frontend/src/shared/api/types.ts`
  - `artifact/frontend/src/features/auth/model/permissions.ts`, `artifact/frontend/src/features/roles/model/permissionGroups.ts`
  - `artifact/frontend/src/features/analysis/api.ts`, `api.spec.ts`, `index.ts`, `model/visibility.ts` and `model/visibility.spec.ts` (new), `components/AnalysisTable.vue`
  - `artifact/frontend/src/pages/RunsPage.vue`, `RunDetailPage.vue`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [x] Types, permission key, API calls.
  - [x] The tabs with the scope in the route query, the Public tag, the Owner column on the shared
    and all tabs.
  - [x] The visibility control with its confirmation, the read-only view of another user's
    analysis, the translations.
- **Tests**:
  - `api.spec.ts`: `scope` reaches the query only when it is not `MINE`; `changeVisibility` sends
    `PUT` with the body.
  - `visibility.spec.ts`: the control for owner (private completed, private running, public),
    another user with and without `canChangeVisibility`.
  - `vue-tsc` (`mise run check`) checks the generated fields and the permission key
    (`AllPermissionsListed`).

### WP5: e2e, screenshots and docs

- **Status**: done
- **Depends on**: WP4
- **Files**:
  - `e2e/tests/support.ts` (a `loginAsNewUser(browser, username, oneTimePassword)` helper: log in,
    change the one-time password, return the page and its context), `e2e/tests/ownership.e2e.ts`
    (uses the helper; the All users tab and `scope=ALL` in place of the switch),
    `e2e/tests/sharing.e2e.ts` (new)
  - `e2e/screenshots/analyses.shot.ts`, `e2e/screenshots/users.shot.ts`, `e2e/screenshots/share.shot.ts` (new),
    `docs/screenshots/analyses.png`, `docs/screenshots/run-share.png` (new), `docs/screenshots/roles.png`, `docs/screenshots/users.png`
  - `README.md`, `docs/coding-convention/backend-java-persistence.md`
- **Steps**:
  - [x] `sharing.e2e.ts`: the admin creates users A and B with ANALYST (unique names, through the
    API) and adds PUBLISHER to A (`PATCH /api/users/{id}`). A logs in, gets a completed analysis
    (`completedAnalysis` with A's request context), opens it, turns **Public** on and confirms. B
    logs in, opens **Shared with everyone**, sees the run with A's username and the Public tag,
    opens it: the report is there, and there is no Stop, Run again, Logs tab or visibility control.
    A turns it private; B reloads and sees "Analysis not found.", and the shared tab no longer lists
    it.
  - [x] Retake and add the screenshots below, update the docs below.
- **Tests**: `sharing.e2e.ts` and the changed `ownership.e2e.ts` (`mise run e2e`).

## Tests

- Unit: `AnalysisAccessTest`, `AnalysisVisibilityServiceTest` and the changed analysis service
  tests carry the rules of the issue; Vitest for the API calls and the visibility control.
- Adapter: `JpaAnalysisRepositoryAdapterTest`, `V4AnalysisVisibilityMigrationTest`,
  `AnalysisPersistenceConventionsTest`, `JpaIdentityRepositoryTest` (PostgreSQL through
  Testcontainers).
- Integration: `AnalysisSharingIntegrationTest` proves the acceptance criteria through the API
  (permission, PUBLISHER after a new login, completed only, B's read-only access, 404 after making
  private, take-back without PUBLISHER, admin take-down, stale version, private stays private);
  `RolePermissionsIntegrationTest` and `RolesApiIntegrationTest` the permission and the role.
- e2e: `sharing.e2e.ts` is the issue's e2e criterion.
- `mise run check` passes.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "6. Watch it and read the results" | A short paragraph on sharing: with `analysis:share` (ADMIN, or the PUBLISHER role) the owner of a completed analysis makes it public with the **Public** switch, after a confirmation; everyone then sees it read-only; it can be made private again at any time, also after losing the permission; an admin can make any public analysis private. With the new screenshot |
| Text | `README.md`, "7. Past analyses" | The tabs **My analyses**, **Shared with everyone**, **All users** (with `analysis:read:all`) in place of the switch; the Public tag; the Owner column on the shared and all tabs |
| Text | `README.md`, "Login" (roles paragraph, permission table, built-in roles of `/api/roles`, the `allUsers` sentence) | `analysis:share` and the built-in PUBLISHER role (held by ADMIN; PUBLISHER holds only it); `PUT /api/analyses/{id}/visibility` with its answers (200, 403 `analysis_share_forbidden`, 404, 409 `analysis_not_shareable`, 409 `concurrent_update`) in the table and the text; another user's public analysis is readable with `analysis:read`, its log, stop and rerun answer 404; `scope=MINE\|SHARED\|ALL` replaces `allUsers=true`; built-in roles are ADMIN, ANALYST, VIEWER and PUBLISHER |
| Text | `README.md`, "Upgrading to 5.0.0" (new, above "Upgrading to 4.0.0") | A client calling `GET /api/analyses?allUsers=true` sends `scope=ALL` instead; existing analyses stay private; ADMIN gets `analysis:share` and the PUBLISHER role is added by the migrations |
| Text | `docs/coding-convention/backend-java-persistence.md`, "Timestamps" table | A row for `ANALYSES.VISIBILITY_CHANGED_AT`: a domain fact, set by the analysis core from the `Clock` when the visibility changes |
| Text | `artifact/frontend/openapi.json`, `shared/api/schema.d.ts` | regenerated |
| Screenshot | `docs/screenshots/analyses.png`, retaken | The Analyses list as the admin on the **All users** tab, the same four replayed runs, one of them made public (Public tag), two ticked for comparing (README "7. Past analyses"); `analyses.shot.ts` goes to `?scope=ALL` and makes one run public through the API |
| Screenshot | `docs/screenshots/run-share.png`, new (`e2e/screenshots/share.shot.ts`) | A completed replayed run's detail page as the admin (its owner), with the confirmation "Everyone on the platform will see this analysis." open on the Public switch (README "6. Watch it and read the results") |
| Screenshot | `docs/screenshots/roles.png`, retaken | The roles list now has PUBLISHER; same data as `roles.shot.ts` (README "Login") |
| Screenshot | `docs/screenshots/users.png`, retaken | The Users page with `maria` holding ANALYST and PUBLISHER (`users.shot.ts` gives her both), the disabled VIEWER `tom` as before (README "3. Log in") |

`docs/event-protocol.md`: unchanged, the runner contract does not change. The other coding
convention documents: unchanged, nothing new in the conventions. The run detail screenshots
`run-live.png`, `run-decision.png`, `run-reports.png`, `run-debates.png`, `run-stats.png` and
`run-table-chart.png` have no shot file in `e2e/screenshots/`, so `mise run screenshots` cannot
retake them; they stay as they are (the new switch is shown in `run-share.png`).

## Out of scope

- **Deleting analyses**: there is still no endpoint that deletes one (`analysis:delete` is granted
  but used by no endpoint). The issue's "only its owner may delete it" and "deleting a public
  analysis removes it for everyone" apply when that endpoint is added, with `canManage`.
- **Audit**: making an analysis public or private becomes an audit event with the audit log of #93,
  which is not there yet.
- What the issue leaves out: sharing with chosen users or groups, public links for people who are
  not logged in, comments or likes, copying or rerunning someone else's analysis as one's own,
  notifications.
- Shot files for the run detail screenshots that have none.

## Open questions

- **`allUsers` removed, so a major version (5.0.0)?** The plan replaces `allUsers` with `scope`, as
  the issue asks, and bumps major because the REST API changes for a client that still sends
  `allUsers=true` (it would silently get its own analyses). The alternative is to keep `allUsers=true`
  as a second spelling of `scope=ALL` for now and bump minor. The plan assumes **major**.
- **Scope values in upper case** (`scope=SHARED`) like `status=COMPLETED`, where the issue writes
  `mine|shared|all`: Spring binds enum parameters by constant name, and upper case keeps one style
  for every enum parameter. The plan assumes **upper case**.
