# Plan: User management UI

- **Issue**: #89 (User management UI)
- **Plan**: 1 of 1 for this issue; depends on: none (the API, #88, is merged)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Admins manage the platform's users in the browser instead of through `/api/users`. A new
**Admin → Users** page, shown to users with `user:read`, lists every user with username, roles,
status, last login and created date. Users with `user:manage` create a user with a one-time password
(generated or typed in) that is shown once to pass on, and per row edit the roles, disable or enable,
reset the password and unlock. When the backend's guard rails would refuse an action (an admin
disabling themselves or removing their own ADMIN role, the last enabled ADMIN disabled or demoted),
the UI says so before the request, and translates the backend's 409 when it comes anyway.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Frontend (`artifact/frontend`) | `docs/coding-convention/frontend-folder-structure.md`: a new feature `features/users/` with `index.ts` as its only entry, `api.ts` as the only file calling `@/shared/api/client`, components in `components/`, pure view logic in `model/` with `*.spec.ts` next to it; the route component in `pages/`, the route in `app/router/index.ts`; API shapes in `shared/api/types.ts` derived from `schema.d.ts` (never edited by hand); translations in `shared/i18n/locales/en.json` and `tr.json`. Other features reached through their `index.ts` only. `docs/coding-convention/repository-shared-types.md`: permission keys come from the generated `Permission` type (`PERMISSIONS … satisfies Record<string, Permission>`). Prettier and ESLint (`boundaries/*`, `no-restricted-imports`) in `mise run check`. |
| Backend | No change. `openapi.json` and `schema.d.ts` already describe `/api/users` and `/api/roles` (#88). |
| End-to-end tests (`e2e/`) | The existing style (`e2e/tests/*.e2e.ts`, `support.ts`, tests in parallel against one platform with the shared admin session from `global-setup.ts`, so a test never changes the admin's password or session); screenshots as `e2e/screenshots/<name>.shot.ts` with `shot()`. Prettier (CI's *End-to-end tests* job). |
| Docs | Root `README.md`: the user-facing feature, the screenshot, the *Login* section, the CI table's e2e row. |

## Design

### API access and types

- `shared/api/types.ts` exports, derived from the generated schema like the other types:
  `User` (`Present<UserDto, 'lockedUntil' | 'lastLoginAt'>` with `roles: UserRole[]`), `UserRole`,
  `Role` (`Present<RoleDto, 'description'>`), `CreateUserRequest`, `UpdateUserRequest`,
  `CreatedUser` (`{ user: User; oneTimePassword: string | null }`) and `OneTimePassword`
  (`{ oneTimePassword: string | null }`).
- `features/users/api.ts`, `usersApi`, one method per endpoint, in the style of `settingsApi`:
  `list()` → `GET /api/users`, `create(request)` → `POST /api/users`, `update(id, request)` →
  `PATCH /api/users/{id}` (with `version`), `resetPassword(id, password?)` →
  `POST /api/users/{id}/password-reset`, `unlock(id)` → `POST /api/users/{id}/unlock`, and
  `listRoles()` → `GET /api/roles`. A blank password is sent as no password, which tells the backend
  to generate one.
- `PERMISSIONS` (`features/auth/model/permissions.ts`) gets `userRead: 'user:read'` and
  `userManage: 'user:manage'`.

### Pure view logic (`features/users/model/`)

- `userStatus.ts`: `userStatus(user)` → `'disabled'` when not enabled, else `'locked'` when locked,
  else `'active'`; the table also shows a "one-time password" tag when `mustChangePassword`.
- `guardRails.ts`: what the backend would refuse, as the error code the backend would answer, or
  `null`:
  - `disableBlocked(target, currentUserId, users)`: `cannot_disable_self` for the caller's own row,
    `last_admin` when the target is the only enabled user with the ADMIN role;
  - `roleChangeBlocked(target, newRoleIds, currentUserId, users, roles)`: `cannot_remove_own_admin`
    when the caller removes ADMIN from themselves, `last_admin` when ADMIN is removed from the only
    enabled ADMIN.
  ADMIN is the role with `builtIn` and name `ADMIN` (from `listRoles()`). The backend stays the
  authority: these only explain early.
- The weak-password messages are `weakPasswordMessages` of the auth feature, exported from
  `features/auth/index.ts` (a feature reaching another through its `index.ts`, as the matrix allows),
  not copied.

### Components (`features/users/components/`)

- `UserManager.vue`: the whole page body. Props `canManage` and `currentUserId`, so the feature does
  not read the session store itself. Loads `list()` and `listRoles()`, shows an `NAlert` on a load
  failure, a **New user** button (with `canManage`), and an `NDataTable` (columns: username, roles as
  tags, status tag, last login (`dateTime`, "never" when null), created; with `canManage` an actions
  column). Row actions: **Edit roles**, **Disable** / **Enable** (`NPopconfirm`), **Reset password**,
  **Unlock** (only on a locked row). An action that `guardRails` blocks is disabled, with an
  `NTooltip` that gives the reason in words. After every change the list reloads. A 409
  `concurrent_update` shows the message and reloads, like `PresetManager`.
- `CreateUserDialog.vue` (`NModal`, preset `card`): username, roles (`NSelect` multiple, labelled by
  name, the description as the option's tooltip, at least one role required), and `PasswordChoice`.
  Field errors from the backend: `invalid_username` and `username_taken` next to the username,
  `unknown_role` next to the roles, `weak_password` with the policy's reasons next to the password.
  On success it emits the created user and the one-time password.
- `PasswordChoice.vue`: a radio **Generate one** (default) / **Enter one**, and the password input
  for the second. Used by the create and the reset dialogs.
- `EditRolesDialog.vue`: the roles `NSelect` with the user's roles; the save button is disabled
  with the reason when `roleChangeBlocked` answers. Sends `roleIds` with the row's `version`. The
  dialog says that the user's sessions end, except when they are the caller's own.
- `ResetPasswordDialog.vue`: `PasswordChoice`; says that the user's sessions end and they must
  change the password at the next login. On the caller's own row it also says they will be logged out
  (the backend ends their session too, and the 401 handler takes them to the login page).
- `OneTimePasswordDialog.vue`: shown after a create or a reset. It shows the username and the
  password in a read-only monospace field with a **Copy** button (`navigator.clipboard`), and says it
  is shown only this once and must be changed at the first login. For an entered password
  (`oneTimePassword` null) it says only that the user must change it at the first login.

### Page, route and navigation

- `pages/UsersPage.vue`: an `NCard` titled **Users** around `UserManager`, passing
  `session.can(PERMISSIONS.userManage)` and `session.user.id`.
- Route `/admin/users`, name `users`, `meta: { permission: PERMISSIONS.userRead }`, lazily loaded
  like the other pages. The permission gate already sends a user without it to the dashboard.
- `App.vue`: an **Admin** entry in the nav, shown with `user:read`: an `NDropdown` with the item
  **Users** (later admin pages, #90's roles and #93's audit log, are added there). It is shown as
  active while the route is under `/admin`.

### Translations

`en.json` and `tr.json` get `nav.admin`, `nav.users`, a `users.*` block (titles, column names,
statuses, actions, dialog texts, the guard-rail explanations, the one-time-password texts) and the
error codes the frontend does not translate yet: `invalid_username`, `username_taken`,
`unknown_role`, `cannot_disable_self`, `cannot_remove_own_admin`, `last_admin`. The guard-rail
error texts say what to do ("Give another enabled user the ADMIN role first.").

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Users feature: API, types, model and translations

- **Depends on**: none
- **Files**: `artifact/frontend/src/shared/api/types.ts`,
  `artifact/frontend/src/features/users/api.ts`, `artifact/frontend/src/features/users/index.ts`,
  `artifact/frontend/src/features/users/model/userStatus.ts` (+ `.spec.ts`),
  `artifact/frontend/src/features/users/model/guardRails.ts` (+ `.spec.ts`),
  `artifact/frontend/src/features/auth/model/permissions.ts`,
  `artifact/frontend/src/features/auth/index.ts`,
  `artifact/frontend/src/shared/i18n/locales/en.json`, `artifact/frontend/src/shared/i18n/locales/tr.json`
- **Steps**:
  - [ ] Add the user and role types to `types.ts`.
  - [ ] Add `usersApi` in `features/users/api.ts`.
  - [ ] Add `userStatus` and the guard-rail functions in `features/users/model/`.
  - [ ] Add `userRead` and `userManage` to `PERMISSIONS`; export `weakPasswordMessages` from the
        auth feature's `index.ts`.
  - [ ] Add the translations (English and Turkish), including the six error codes.
- **Tests**: `userStatus.spec.ts` (each status, disabled wins over locked); `guardRails.spec.ts`
  (own disable, last enabled ADMIN disabled, a second enabled ADMIN lifts it, a disabled second ADMIN
  does not, own ADMIN removal, ADMIN removed from the last enabled ADMIN, a role change without ADMIN
  involved is not blocked).

### WP2: Users page: components, route and navigation

- **Depends on**: WP1
- **Files**: `artifact/frontend/src/features/users/components/UserManager.vue`,
  `CreateUserDialog.vue`, `EditRolesDialog.vue`, `ResetPasswordDialog.vue`, `PasswordChoice.vue`,
  `OneTimePasswordDialog.vue` (all in `features/users/components/`),
  `artifact/frontend/src/features/users/index.ts`, `artifact/frontend/src/pages/UsersPage.vue`,
  `artifact/frontend/src/app/router/index.ts`, `artifact/frontend/src/app/App.vue`
- **Steps**:
  - [ ] Build the components as in the design; export `UserManager` from `index.ts`.
  - [ ] Add `UsersPage.vue` and the `/admin/users` route.
  - [ ] Add the **Admin → Users** navigation, shown with `user:read`.
  - [ ] `mise run check` passes (type-check, ESLint boundaries, Prettier, Knip).
- **Tests**: the components are covered by the e2e test of WP3 (the repository has no component
  tests; the logic lives in the model of WP1).

### WP3: End-to-end test, screenshot, docs and version

- **Depends on**: WP2
- **Files**: `e2e/tests/users.e2e.ts`, `e2e/screenshots/users.shot.ts`, `docs/screenshots/users.png`,
  `README.md`, `gradle.properties`, `artifact/frontend/package.json`,
  `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] `users.e2e.ts`, as the shared admin (its session and password are never changed):
    - the issue's acceptance flow: the admin opens **Admin → Users**, creates a user with a unique
      name (`e2e-user-<timestamp>`, the tests run in parallel and are retried) and the VIEWER role
      with a generated password, and reads it from the one-time-password dialog. In a new browser
      context (no stored session), the new user logs in with it, is sent to the change-password
      page, sets a new password and reaches the dashboard, with no **Admin** entry in the nav. The
      admin disables the user in the table. The user's next navigation lands on the login page, and
      logging in again answers "Wrong username or password.";
    - a guard rail: on the admin's own row, **Disable** is disabled and its tooltip explains why.
  - [ ] `users.shot.ts`: creates over the API an ANALYST and a VIEWER, disables the VIEWER, then
        takes `users` of `/admin/users` (role IDs from `GET /api/roles`).
  - [ ] Run `mise run screenshots users` and commit `docs/screenshots/users.png`.
  - [ ] Update `README.md` (see *Docs to update*).
  - [ ] `mise run version:bump minor`.
- **Tests**: `users.e2e.ts` proves the issue's acceptance criterion; the e2e job runs it in CI.

## Tests

- Vitest: `userStatus.spec.ts` and `guardRails.spec.ts` (WP1), with the existing specs still green.
- `mise run check`: type-check (`vue-tsc`, which also checks the new `PERMISSIONS` keys against the
  generated `Permission`), ESLint boundaries, Prettier, Knip.
- End-to-end: `e2e/tests/users.e2e.ts` is the issue's "done when": an admin creates a user, the user
  logs in with the one-time password and must change it, then the admin disables the user and the
  user is logged out. It runs in CI's *End-to-end tests* job; locally `mise run e2e` (needs Podman
  or Docker).
- No backend tests: the backend does not change; #88's integration tests cover the endpoints and
  the guard rails.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, Quick start *3. Log in* | A paragraph after it on adding users: **Admin → Users**, **New user** with a generated or entered one-time password shown once, the user changes it at the first login; the row actions; disabling instead of deleting; the guard rails in one sentence; who sees the page (`user:read`, changes `user:manage`). |
| Text | `README.md`, *Development → Login* | One bullet: the UI's **Admin → Users** page (`/admin/users`) uses these endpoints, explains a blocked guard rail before the request, and shows the one-time password once. |
| Text | `README.md`, *Continuous integration*, the *End-to-end tests* row | Add "user management (create a user, first login with the one-time password, disable)" to the list, and correct "as a seeded `e2e` user" to "as the first admin" (what `e2e/tests/support.ts` does). |
| Screenshot | `docs/screenshots/users.png`, new | The **Users** page at 1440×900, dark theme: `admin` (ADMIN), an active ANALYST and a disabled VIEWER, with the actions column. Shown in the README's new paragraph under *3. Log in*. Taken by `e2e/screenshots/users.shot.ts`. |

## Out of scope

- Custom roles and an **Admin → Roles** page: #90.
- The audit log page: #93.
- Showing only the user's own analyses and presets: #91, #92.
- Deleting users: the API has no hard delete (#88).
