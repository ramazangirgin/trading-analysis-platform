# Plan: RBAC endpoint permissions and the ANALYST and VIEWER roles

- **Issue**: #87 (RBAC: permission catalogue, built-in roles and endpoint checks)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every `/api/**` endpoint needs a specific permission instead of only a session. The built-in
ANALYST and VIEWER roles exist next to ADMIN, so the user management API (#88) can assign them. A
VIEWER can read analyses and presets but gets 403 on starting, stopping and rerunning a run, on
preset changes and on API key writes. The UI hides the actions and pages the user has no permission
for. An endpoint added later without a permission rule is refused for everyone (fail closed), and a
test fails the build until it gets one.

## What is already there

The issue predates #79 to #86, which already did part of it. These stay as they are:

- `Permission` (`domain.identity.core.model`) holds the whole catalogue of the issue, with the
  `analysis:read:all` style keys; the PostgreSQL enum `"IDENTITY"."PERMISSION"` mirrors it.
- The built-in ADMIN role is seeded by `V3__seed_admin_role.sql` with every permission, fixed ID
  `RoleId.ADMIN`; `FirstAdminService` gives the first admin that role (acceptance criterion 2).
- A login puts the user's permission keys (and `ROLE_<name>`) into the session as
  `SimpleGrantedAuthority` values (`AuthApiDelegateImpl#authorities`), and `GET /api/auth/me`
  returns `roles` and `permissions` (`CurrentUserDto`).
- `SecurityConfig` already checks one endpoint by permission: `POST /api/users/*/unlock` needs
  `user:manage`.
- The frontend's `useSessionStore` has `can(permission)`, unused so far.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend | `backend-java-package-structure.md`: Spring Security code lives in `bff.delegate.impl`, never in `bff.controller.api` (`bff_api_holds_no_security_code`), so **no annotation on controllers** as the issue sketched: an endpoint's permission is an annotation on its delegate method in `bff.delegate.impl` (the endpoint's business logic), next to the code it protects; the annotation type and its interceptor live in `bff.delegate.impl.config`, since the delegate root holds only delegate implementations (`delegate_root_holds_only_delegate_impls`). `backend-database-naming.md` and `backend-java-persistence.md`: quoted uppercase names, the identity schema's own Flyway history, permissions as the enum array on `"ROLES"`, labels are the Java constant names. `backend-java-checkstyle.md`, `backend-java-formatting.md`. |
| frontend | `frontend-folder-structure.md`: permission logic in `features/auth` (pure helpers in `model/` with `*.spec.ts`, exported through `index.ts`), pages and `app/` ask `useSessionStore().can(...)`; i18n strings in `shared/i18n/locales/{en,tr}.json`. |
| docs | Root `README.md` "Login" section; `backend-java-package-structure.md` (where an endpoint's permission rule goes). |

## Design

### Built-in roles (backend, identity adapter)

- New migration
  `domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/V4__seed_analyst_and_viewer_roles.sql`
  inserts two rows into `"IDENTITY"."ROLES"`, with fixed IDs equal to their names and
  `"BUILT_IN" = TRUE`, following `V3__seed_admin_role.sql`:
  - `ANALYST`, "Runs analyses, reads and deletes their own, manages their own presets, reads the
    settings": `ANALYSIS_RUN`, `ANALYSIS_READ`, `ANALYSIS_DELETE`, `PRESET_READ`, `PRESET_MANAGE`,
    `SETTINGS_READ`.
  - `VIEWER`, "Reads their own analyses and presets": `ANALYSIS_READ`, `PRESET_READ`.
  "Their own" becomes true with the ownership issues (#91, #92); until then the read permissions
  cover every analysis and preset, as today.
- `RoleId` gets `ANALYST` and `VIEWER` constants next to `ADMIN`, with the same Javadoc pointing at
  the migration. Fix the reference in `V3__seed_admin_role.sql`'s comment only if it is wrong;
  never edit an applied migration otherwise (Flyway checksums).
- "Not deletable": there is no role deletion or role edit yet; #90 (custom roles) adds them and must
  refuse built-in roles (`Role#builtIn`). Nothing to enforce in this plan.

### Endpoint permissions (backend, BFF)

Each endpoint's permission is declared on its delegate method (`*ApiDelegateImpl` in
`bff.delegate.impl`), not in `SecurityConfig`: the definition stays next to the endpoint's
business logic (changed after the first review, see WP5). WP2 first put the rules into
`SecurityConfig`'s request matchers; WP5 moves them.

- A custom annotation `@RequiresPermission(Permission[] value)` in `bff.delegate.impl.config`
  (`@Target(METHOD)`, `@Retention(RUNTIME)`, `@Documented`): the caller needs **any** of the given
  permissions. It takes the `Permission` enum, not key strings, so a typo does not compile and no
  SpEL is involved. Example: `@RequiresPermission(Permission.ANALYSIS_RUN)` on
  `AnalysesApiDelegateImpl#startAnalysis`, `@RequiresPermission({Permission.ANALYSIS_RUN,
  Permission.SETTINGS_READ})` on `CatalogApiDelegateImpl#getCatalog`.
- Spring Security method security enforces it: `@EnableMethodSecurity` (pre/post annotations not
  needed for this) plus an infrastructure `Advisor` bean
  (`AuthorizationManagerBeforeMethodInterceptor` with an `AnnotationMatchingPointcut` on
  `@RequiresPermission` and an `AuthorizationManager<MethodInvocation>` that reads the annotation
  and checks the authentication's authorities against `Permission#key()`). Both in
  `bff.delegate.impl.config` (a `MethodSecurityConfig` next to `SecurityConfig`, or in it). A
  refused call throws `AccessDeniedException`; `ApiExceptionHandler` has no catch-all handler, so
  `ExceptionTranslationFilter` turns it into the existing 403 `forbidden` from
  `ApiAccessDeniedHandler`. Check that this holds for the SSE endpoint too (the check runs before
  the delegate returns the emitter).
- `SecurityConfig` keeps only what is not about one endpoint's permission: the ASYNC dispatcher
  rule, the open routes (`/api/auth/login`, `/api/auth/logout`, `GET /api/auth/me`,
  `/api/openapi`, `/api/openapi.yaml`, `/api/openapi/**`, `/actuator/**`), and
  `.requestMatchers("/api/**").authenticated()` for the rest (401 without a session before any
  delegate runs). The per-endpoint matchers, the `denyAll()` catch-all and the unlock rule go.
  `POST /api/auth/password` needs only the session, as today.
- Fail closed: without the URL `denyAll()`, a delegate method without `@RequiresPermission` would
  be open to every session. `EndpointPermissionsIntegrationTest` still catches it (a user without
  roles must get 403 everywhere outside the allow list). A static check that every delegate method
  carries a security annotation is the follow-up issue #171, not this plan.

The permissions per endpoint (the delegate method of each):

| Endpoint | Permission |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `/api/openapi/**`, `/actuator/**` | open (unchanged) |
| `POST /api/auth/password` | a session only (`authenticated()`): every user changes their own password |
| `POST /api/analyses`, `POST /api/analyses/*/stop`, `POST /api/analyses/*/rerun` | `analysis:run` |
| `GET /api/analyses`, `GET /api/analyses/*`, `GET /api/analyses/*/report`, `/prices`, `/logs`, `/events` | `analysis:read` |
| `POST /api/reports/rescan` | `analysis:run` (it creates and updates analyses from the data folder, as runs do) |
| `GET /api/catalog` | `analysis:run` or `settings:read` (`hasAnyAuthority`): the new-analysis form and the key table on the settings page need it |
| `GET /api/health` | `analysis:run` or `settings:read`: its alerts are about running (runner, Docker, missing keys) |
| `GET /api/secrets` | `settings:read` |
| `PUT /api/secrets/*`, `DELETE /api/secrets/*` | `settings:keys:write` |
| `GET /api/presets` | `preset:read` |
| `POST /api/presets`, `PUT /api/presets/*`, `DELETE /api/presets/*` | `preset:manage` |
| `POST /api/users/*/unlock` | `user:manage` (moves from `SecurityConfig` to `UsersApiDelegateImpl`) |

The ASYNC dispatcher rule stays first, and `PasswordChangeRequiredFilter` keeps answering
`password_change_required` before the permission check matters. A refused request gets the existing
403 `forbidden` from `ApiAccessDeniedHandler`. The permission strings `analysis:delete`,
`analysis:read:all`, `preset:read:all`, `user:read`, `role:manage` and `audit:read` guard no
endpoint yet: there is no analysis delete endpoint (the issue's "delete" for a VIEWER is proved on
the preset and secret delete endpoints), and the others come with #88, #90, #91, #92 and #93, each
annotating its own delegate methods.

Update `SecurityConfig`'s class Javadoc: every other `/api` route needs a session, and each
endpoint's permission is the `@RequiresPermission` on its delegate method.

### The guard against a forgotten endpoint (backend, test)

The runtime guard is an integration test, `EndpointPermissionsIntegrationTest` in
`artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/`. It reads every
handler method from Spring's `RequestMappingHandlerMapping` (`requestMappingHandlerMapping` bean),
fills path variables with a random ID, and for each one sends the request:

- from a session whose user has no role: every endpoint answers 403, except the open ones and
  `POST /api/auth/password` (an explicit, short allow list in the test);
- from an ADMIN session: no endpoint answers 403 (any 2xx, 400 or 404 is fine, a random ID gives
  404). This fails when an endpoint's permission is wrong for ADMIN. The SSE endpoint is sent with
  `Accept: text/event-stream` and read only up to the status line.

Unsafe requests carry the CSRF header (via `TestSession`) and a body of `{}` where the endpoint
consumes JSON, so a 403 can only come from the permission check.

### Tests that log in

`TestSession.loggedIn(port, users, hasher)` creates a user without roles, who now gets 403 almost
everywhere. Give it a role argument, `loggedIn(port, users, hasher, RoleId... roles)` (and
`createUser` the same), and pass `RoleId.ADMIN` (or `RoleId.ANALYST` where the test is about an
analyst's work) in `AnalysesApiIntegrationTest`, `PresetsApiIntegrationTest`,
`PasswordChangeIntegrationTest`, `AuthApiIntegrationTest` and `LoginLockoutIntegrationTest`; keep a
role-less user where a test means "a user without the permission"
(`unlockNeedsASessionAndTheUserManagePermission`). Check `TradingPlatformApplicationTests` (its
`isForbidden` case) still means what it says.

### UI (frontend)

- `features/auth/model/permissions.ts`: the permission keys the UI uses as constants
  (`PERMISSIONS.analysisRun = 'analysis:run'`, `analysisRead`, `presetManage`, `settingsRead`,
  `settingsKeysWrite`), and `permissionGateRedirect(can, to)`, built like `passwordGateRedirect`:
  a route whose `meta.permission` (one key or a list of which any is enough) the user lacks
  redirects to `dashboard`. Export both from `features/auth/index.ts`.
- `app/router/index.ts`: `meta.permission` on `new-analysis` (`analysis:run`) and `settings`
  (`settings:read`); the analyses, compare and reports routes need `analysis:read`. `beforeEach`
  applies the password gate first, then the permission gate. The dashboard stays open to every
  logged-in user, so the redirect never loops.
- `app/App.vue`: show the "New analysis" link only with `analysis:run`, "Settings" only with
  `settings:read`, "Analyses" and "Reports" only with `analysis:read`.
- `pages/DashboardPage.vue`: the "Start" button only with `analysis:run`; `HealthAlerts` only with
  `analysis:run` or `settings:read` (it calls `/api/health`).
- `pages/RunsPage.vue`: "Scan data dir" and "New analysis" only with `analysis:run`.
- `pages/RunDetailPage.vue`: "Stop" and "Rerun" only with `analysis:run`.
- `pages/SettingsPage.vue`: the new-value input and the set/remove buttons of the key table only
  with `settings:keys:write` (the status column stays); `PresetManager` gets a `readonly` prop
  (from `!can('preset:manage')`) that hides create, edit and delete. If the page loads presets with
  only `settings:read`, load them only with `preset:read`.
- `shared/i18n/locales/en.json` and `tr.json`: an error label for `forbidden` ("You do not have
  permission for this." / "Bunun için yetkiniz yok."), next to `unauthorized`, so a 403 that
  slips through reads properly.

The UI only hides; the backend rules are what protect.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: ANALYST and VIEWER roles

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/V4__seed_analyst_and_viewer_roles.sql` (new)
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/model/RoleId.java`
  - `artifact/backend/domain/identity/adapter/src/test/java/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [x] Write the migration with a header comment in the style of V3 (what it seeds, why the IDs
        are fixed, that a new `Permission` needs a migration deciding which built-in roles get it).
  - [x] Add `RoleId.ANALYST` and `RoleId.VIEWER`.
- **Tests**: `JpaIdentityRepositoryTest` loads `RoleId.ANALYST` and `RoleId.VIEWER` (next to the
  existing ADMIN case) and asserts name, `builtIn` and the exact permission sets; a user saved with
  one of them round-trips.

### WP2: Permission rules in the security chain

- **Status**: done
- **Depends on**: WP1 (the tests log in with the new roles)
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/SecurityConfig.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/TestSession.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/EndpointPermissionsIntegrationTest.java` (new)
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/RolePermissionsIntegrationTest.java` (new)
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/{AnalysesApiIntegrationTest,PresetsApiIntegrationTest,PasswordChangeIntegrationTest,AuthApiIntegrationTest,LoginLockoutIntegrationTest,TradingPlatformApplicationTests}.java`
- **Steps**:
  - [x] Replace the catch-all with the rules of the table and `denyAll()`; update the Javadoc.
  - [x] Give `TestSession.loggedIn` and `createUser` the role argument; update the callers.
  - [x] Write the two new tests.
- **Tests**:
  - `EndpointPermissionsIntegrationTest` (above): no endpoint without a rule, every rule refuses a
    user without roles.
  - `RolePermissionsIntegrationTest`: a VIEWER gets 403 on `POST /api/analyses`,
    `POST /api/analyses/{id}/stop`, `POST /api/analyses/{id}/rerun`, `POST /api/reports/rescan`,
    `PUT` and `DELETE /api/secrets/{name}`, `POST`, `PUT` and `DELETE /api/presets…`,
    `GET /api/secrets`, `GET /api/catalog`, `GET /api/health`, and gets 200 on `GET /api/analyses`
    and `GET /api/presets`; an ANALYST gets 403 on the secret writes and the unlock and not on the
    run endpoints; `GET /api/auth/me` returns each role's permission keys; the first admin
    (`FirstAdminIntegrationTest` setup, or a user with `RoleId.ADMIN`) has `roles: ["ADMIN"]` and
    every permission key.
  - The updated existing integration tests still pass.

### WP3: UI follows the permissions

- **Status**: done
- **Depends on**: none (the `me` response already carries the permissions)
- **Files**:
  - `artifact/frontend/src/features/auth/model/permissions.ts` (new), `permissions.spec.ts` (new)
  - `artifact/frontend/src/features/auth/index.ts`
  - `artifact/frontend/src/app/router/index.ts`, `artifact/frontend/src/app/App.vue`
  - `artifact/frontend/src/pages/{DashboardPage,RunsPage,RunDetailPage,SettingsPage}.vue`
  - `artifact/frontend/src/features/settings/components/PresetManager.vue`
  - `artifact/frontend/src/shared/i18n/locales/{en,tr}.json`
  - component specs next to the changed components where the feature has them
- **Steps**:
  - [x] Permission constants and `permissionGateRedirect`.
  - [x] Route meta and the gate in `beforeEach`.
  - [x] Hide the links, buttons and the key/preset editing as in the design.
  - [x] The `forbidden` error label in both locales.
- **Tests**: `permissions.spec.ts`: the gate lets a route without `meta.permission` through,
  redirects to `dashboard` without the permission, accepts any of a list, and never redirects the
  dashboard. A spec for `PresetManager` in read-only mode (no create, edit, delete) and, if the
  feature has component specs for the settings page parts, for the key table without
  `settings:keys:write`. `store.spec.ts` covers `can` if it does not yet.

### WP4: Docs

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**: `README.md`, `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [x] As in "Docs to update".
- **Tests**: none (`mise run check` runs the Markdown checks).

### WP5: Permissions on the delegate methods

- **Status**: done
- **Depends on**: WP2, WP4 (it moves WP2's rules and corrects WP4's docs)
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/RequiresPermission.java` (new)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/MethodSecurityConfig.java` (new, or the beans in `SecurityConfig`)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/SecurityConfig.java`
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/{AnalysesApiDelegateImpl,ReportsApiDelegateImpl,CatalogApiDelegateImpl,HealthApiDelegateImpl,SettingsApiDelegateImpl,UsersApiDelegateImpl}.java`
  - a unit test for the authorization manager in `artifact/backend/bff/impl/src/test/java/…/bff/delegate/impl/config/`
  - `README.md`, `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [x] `@RequiresPermission` and the method-security beans that enforce it.
  - [x] Annotate every delegate method of the table with its permission(s); `AuthApiDelegateImpl`
        stays unannotated (its routes are open or need only a session, set in `SecurityConfig`).
  - [x] Remove the per-endpoint matchers, the unlock matcher and `denyAll()` from `SecurityConfig`;
        `/api/**` back to `authenticated()`; update its Javadoc.
  - [x] Correct the docs (README "Login" and the package-structure row) to the delegate annotation.
- **Tests**: a unit test of the authorization manager: granted with one of several permissions,
  denied without, denied for an anonymous authentication. `EndpointPermissionsIntegrationTest`,
  `RolePermissionsIntegrationTest` and the other integration tests pass unchanged: the HTTP
  behaviour (401 without a session, 403 `forbidden` without the permission) is the same.

### WP6: Progress comment script exits cleanly

- **Status**: done (a937fa7, in the developer's session while the agent loop of this pull request ran)
- **Depends on**: none
- **Files**: `scripts/agent/progress.sh`
- **Steps**:
  - [x] `post()` kept the lock path in a `local`, but its EXIT trap (`rm -f "$lock"; agent_exit`)
        runs after `post()` returned: with `set -u` every call ended with `lock: unbound variable`
        and exit 1 after a successful post, left `progress.lock` behind, and made the callers
        report "the progress comment on the issue was not updated". The lock path is global now.
  - [x] The trap's `rm` also hid the script's exit status from `agent_exit` (which reads `$?`), so
        a failed call was recorded as successful: `release_lock` removes the lock and returns the
        status it found.
- **Tests**: by hand: `progress.sh post 170` exits 0, posts, and leaves no `progress.lock`. The
  shell scripts have no test harness (only the Python helpers have `test_*.py`).

### WP7: One permission catalogue, shared through OpenAPI

Added after the third review, at the developer's request: the permission keys were written by hand
again in `artifact/frontend/src/features/auth/model/permissions.ts`, a copy nothing checks. The
domain enum `Permission` stays the one source, and every other layer gets it in a checked way.
Documented as the generic approach for any value set the backend domain, the BFF and the frontend
share.

- **Status**: done
- **Depends on**: WP3, WP5
- **Design**:
  - BFF API: a DTO enum `PermissionEnumDto` in `bff.controller.api.model` (the API layer has its own
    types, it does not use domain types). Each constant serialises to its key (`analysis:run`)
    through `@JsonValue` on a `key()` accessor; `CurrentUserDto.permissions` becomes
    `List<PermissionEnumDto>` (login, `me` and password responses). JSON on the wire stays the same.
  - Mapping: `PermissionToPermissionEnumDtoMapper` in `bff.delegate.impl.mapper`, a MapStruct enum
    mapping (`SourceToTargetMapper` with one `map` method, as the other mappers). MapStruct fails the
    compile when a `Permission` constant has no `PermissionEnumDto` counterpart. No mapper unit test:
    MapStruct cannot see the DTO's key strings, but `RolePermissionsIntegrationTest` already
    asserts that ADMIN (who holds every permission) gets exactly `Permission::key` of every
    constant from `me`, which checks every wire key end to end. `AuthApiDelegateImpl` builds the
    list through the mapper instead of `Permission::key`.
  - OpenAPI: `openapi.json` shows the permissions as an enum whose values are the keys. Verify that
    springdoc takes the `@JsonValue` keys, not the constant names; if it does not, make it (for
    example a schema annotation derived from the enum, never a second hand-written list).
  - Frontend: `mise run api-types` regenerates `shared/api/schema.d.ts`; `shared/api/types.ts`
    exports `Permission` from it (only `types.ts` imports the schema). `permissions.ts` keeps the
    friendly names only, `PERMISSIONS = { … } satisfies Record<string, Permission>`; `can()`,
    `permissionGateRedirect` and the route `meta.permission` take `Permission`, so an unknown or
    removed key fails `vue-tsc` (`mise run check`).
  - Database: unchanged. `"IDENTITY"."PERMISSION"` stays the enum mirror the persistence convention
    asks for, guarded by `JpaIdentityRepositoryTest`.
- **Files**:
  - `artifact/backend/bff/api/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/controller/api/model/{PermissionEnumDto,CurrentUserDto}.java`
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper/PermissionToPermissionEnumDtoMapper.java` (new)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/AuthApiDelegateImpl.java`
  - `openapi.json` (wherever `mise run api-types` reads it), `artifact/frontend/src/shared/api/{schema.d.ts,types.ts}`
  - `artifact/frontend/src/features/auth/{model/permissions.ts,store.ts}`, `artifact/frontend/src/app/router/index.ts` and any caller whose types change
  - `docs/coding-convention/repository-shared-types.md` (new), `docs/coding-convention/README.md`
- **Steps**:
  - [x] `PermissionEnumDto`, the typed `CurrentUserDto.permissions` and the mapper.
  - [x] Regenerate the OpenAPI document and the frontend API types; type the frontend with
        `Permission`.
  - [x] Write `repository-shared-types.md` and list it in the conventions README table.
  - [x] README "Login": say that the permission is checked after request validation. Since WP5 the
        check runs on the delegate method, after the controller's `@Valid`, so a request without
        the permission but with an invalid body answers 400 with the validation errors, and only a
        valid one reaches the check and gets 403 `forbidden`. Accepted by the developer as Spring's
        usual method-security order; no behaviour change.
- **Convention** (`docs/coding-convention/repository-shared-types.md`, "Whole repository: values
  shared by the backend domain, the BFF and the frontend"), in the plain style of the other
  convention documents, with permissions as the worked example:
  - the domain enum is the single source of the values;
  - the BFF API has a DTO enum mirror, mapped by a MapStruct enum mapping: the compile fails on a
    missing constant. The wire values (the DTO's own strings, which MapStruct cannot see) are
    checked by one integration assertion where a response carries every value (for permissions:
    ADMIN's `me`), not by an extra mapper unit test;
  - OpenAPI carries the values to the frontend as an enum; the frontend uses the generated type
    (`shared/api/types.ts`) and never hand-writes the set; constants for readability are checked
    with `satisfies`;
  - a database enum, where the persistence convention asks for one, is a mirror with a test
    against the Java enum;
  - what to do when a value is added (domain constant, DTO constant, migration, `mise run
    api-types`) and which check fails at each missing step.
- **Tests**: no new backend test (see Mapping). `vue-tsc` in `mise run check` (a wrong key in
  `permissions.ts` must fail it); the existing `RolePermissionsIntegrationTest` and
  `AuthApiIntegrationTest` assertions on the `me` permissions stay green, since the JSON does not
  change, and keep guarding the wire keys.

## Tests

- Backend: `JpaIdentityRepositoryTest` (roles seeded on PostgreSQL), `RolePermissionsIntegrationTest`
  proves the "done when": a VIEWER gets 403 on run, delete (presets, secrets) and key writes, and
  the first admin has ADMIN. `EndpointPermissionsIntegrationTest` proves "every BFF endpoint has a
  permission check". ArchUnit keeps the security code out of `bff.controller.api`.
- Frontend: Vitest specs for the gate and the read-only states.
- End-to-end: no new test. A non-admin user cannot be created through the API before #88, and the
  e2e tests log in as the first admin, who keeps every permission, so the existing tests must stay
  green unchanged. #88/#89 add e2e coverage of the other roles.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md` → "Development" → "Login", "The backend" list | Replace "Every other `/api/**` route answers 401 without a session" with: each route needs a permission (401 without a session, 403 `forbidden` without the permission), and a short table of the built-in roles (ADMIN: everything; ANALYST: run, read and delete analyses, manage presets, read settings; VIEWER: read analyses and presets) and of which permission each endpoint group needs (the table in "Endpoint permissions", condensed). Say that the first admin has ADMIN, that "their own" becomes true with ownership (#91, #92), and that the UI hides what the user may not do. |
| Text | `README.md` → "Quick start" → "3. Log in" | One sentence: the first admin has the ADMIN role, with every permission. Only if the section does not say so already. |
| Text | `docs/coding-convention/backend-java-package-structure.md` → "Where each class kind goes" | A row (or a note under the Spring Security row): an endpoint's permission is `@RequiresPermission` on its delegate method in `bff.delegate.impl`, never an annotation on a controller nor a request matcher in `SecurityConfig`; the annotation type and its enforcement live in `bff.delegate.impl.config`, and `EndpointPermissionsIntegrationTest` fails while an endpoint lacks it. |
| Text | `README.md` → "Development" → "Login" | The order of the checks: 401 without a session, then request validation (400), then the endpoint's permission (403 `forbidden`). |
| Screenshot | none | The screenshots log in as the first admin, who sees every link and button, so no page changes for them. |

## Out of scope

- Deleting or editing roles and the guard that built-in roles cannot be deleted: #90.
- Assigning roles to users (`POST/PATCH /api/users`): #88; the UI for it: #89.
- Ownership ("their own" analyses and presets, `analysis:read:all`, `preset:read:all`, 404 instead
  of 403 for someone else's resource): #91, #92.
- An analysis delete endpoint (`analysis:delete` stays in the catalogue for it): not planned in
  #78; a separate issue when wanted.
- `audit:read` endpoints: #93.
- A static check (a simple Checkstyle check, or ArchUnit) that every delegate method carries `@RequiresPermission` or an explicit marker: #171.

## Open questions

None left. Decisions made in this plan, for the plan review:

- Permission checks are a custom `@RequiresPermission(Permission...)` on the delegate methods
  (decided after the first review: the definition belongs next to the endpoint's business logic),
  not request matchers in `SecurityConfig` and not annotations on the controllers
  (`bff_api_holds_no_security_code`). A custom annotation rather than `@PreAuthorize`: it takes
  the enum, needs no SpEL strings, and gives the follow-up check one annotation to look for.
- The runtime guard is an integration test over every handler mapping. A static check that every
  delegate method carries a security annotation is #171.
- `POST /api/reports/rescan` needs `analysis:run`; `GET /api/catalog` and `GET /api/health` need
  `analysis:run` or `settings:read`, so a VIEWER's dashboard shows no health alerts.
