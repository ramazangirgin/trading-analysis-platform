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
| backend | `backend-java-package-structure.md`: Spring Security code lives in `bff.delegate.impl.config`, never in `bff.controller.api` (`bff_api_holds_no_security_code`), so **no `@PreAuthorize` on controllers** as the issue sketched: the rules go into `SecurityConfig`'s request matchers, as the unlock rule does. `backend-database-naming.md` and `backend-java-persistence.md`: quoted uppercase names, the identity schema's own Flyway history, permissions as the enum array on `"ROLES"`, labels are the Java constant names. `backend-java-checkstyle.md`, `backend-java-formatting.md`. |
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

`SecurityConfig#securityFilterChain` gets one request matcher per endpoint (method and path), in the
order of the controllers, replacing the catch-all `.requestMatchers("/api/**").authenticated()` with
`.requestMatchers("/api/**").denyAll()`. The rules, all through `Permission.X.key()`:

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
| `POST /api/users/*/unlock` | `user:manage` (unchanged) |
| any other `/api/**` | `denyAll()` |

The ASYNC dispatcher rule stays first, and `PasswordChangeRequiredFilter` keeps answering
`password_change_required` before the permission check matters. A refused request gets the existing
403 `forbidden` from `ApiAccessDeniedHandler`. The permission strings `analysis:delete`,
`analysis:read:all`, `preset:read:all`, `user:read`, `role:manage` and `audit:read` guard no
endpoint yet: there is no analysis delete endpoint (the issue's "delete" for a VIEWER is proved on
the preset and secret delete endpoints), and the others come with #88, #90, #91, #92 and #93, each
adding its rules here.

To keep the class readable, the matchers may move into a private method
`authorizeApi(AuthorizeHttpRequestsConfigurer<...>.AuthorizationManagerRequestMatcherRegistry)` in
`SecurityConfig`; no new class. Update the class Javadoc ("every other `/api` route needs a
session" → "needs its permission; a route without a rule is refused").

### The guard against a forgotten endpoint (backend, test)

The issue's optional Checkstyle or ArchUnit rule cannot see request matchers, so the guard is an
integration test instead, `EndpointPermissionsIntegrationTest` in
`artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/`. It reads every
handler method from Spring's `RequestMappingHandlerMapping` (`requestMappingHandlerMapping` bean),
fills path variables with a random ID, and for each one sends the request:

- from a session whose user has no role: every endpoint answers 403, except the open ones and
  `POST /api/auth/password` (an explicit, short allow list in the test);
- from an ADMIN session: no endpoint answers 403 (any 2xx, 400 or 404 is fine, a random ID gives
  404). This fails when a new endpoint falls into `denyAll()`. The SSE endpoint is sent with
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
  - [ ] Permission constants and `permissionGateRedirect`.
  - [ ] Route meta and the gate in `beforeEach`.
  - [ ] Hide the links, buttons and the key/preset editing as in the design.
  - [ ] The `forbidden` error label in both locales.
- **Tests**: `permissions.spec.ts`: the gate lets a route without `meta.permission` through,
  redirects to `dashboard` without the permission, accepts any of a list, and never redirects the
  dashboard. A spec for `PresetManager` in read-only mode (no create, edit, delete) and, if the
  feature has component specs for the settings page parts, for the key table without
  `settings:keys:write`. `store.spec.ts` covers `can` if it does not yet.

### WP4: Docs

- **Depends on**: WP1, WP2, WP3
- **Files**: `README.md`, `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [ ] As in "Docs to update".
- **Tests**: none (`mise run check` runs the Markdown checks).

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
| Text | `docs/coding-convention/backend-java-package-structure.md` → "Where each class kind goes" | A row (or a note under the Spring Security row): an endpoint's permission rule is a request matcher in `SecurityConfig`, never `@PreAuthorize` on a controller; a route without a rule is refused (`denyAll`), and `EndpointPermissionsIntegrationTest` fails until it has one. |
| Screenshot | none | The screenshots log in as the first admin, who sees every link and button, so no page changes for them. |

## Out of scope

- Deleting or editing roles and the guard that built-in roles cannot be deleted: #90.
- Assigning roles to users (`POST/PATCH /api/users`): #88; the UI for it: #89.
- Ownership ("their own" analyses and presets, `analysis:read:all`, `preset:read:all`, 404 instead
  of 403 for someone else's resource): #91, #92.
- An analysis delete endpoint (`analysis:delete` stays in the catalogue for it): not planned in
  #78; a separate issue when wanted.
- `audit:read` endpoints: #93.

## Open questions

None left. Decisions made in this plan, for the plan review:

- Permission checks are request matchers in `SecurityConfig`, not `@PreAuthorize`, because of the
  documented `bff_api_holds_no_security_code` rule.
- The optional build rule is an integration test over every handler mapping plus `denyAll()` as
  the default, not Checkstyle or ArchUnit, which cannot see the request matchers.
- `POST /api/reports/rescan` needs `analysis:run`; `GET /api/catalog` and `GET /api/health` need
  `analysis:run` or `settings:read`, so a VIEWER's dashboard shows no health alerts.
