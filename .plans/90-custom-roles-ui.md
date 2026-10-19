# Plan: Custom roles UI

- **Issue**: #90 (RBAC: custom roles API and UI)
- **Plan**: 2 of 2 for this issue; depends on: `90-custom-roles-api`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

An **Admin → Roles** page lets admins with `role:manage` see every role with its permissions and
number of users, create custom roles, rename them, change their permissions with a checklist
grouped by area (analysis, preset, settings, user, audit), and delete them. The page shows
beforehand what the backend refuses: renaming or deleting a built-in role, changing ADMIN's
permissions, and deleting a role that users still hold.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| frontend (`artifact/frontend`) | `frontend-folder-structure.md`: a new feature `features/roles/` (`api.ts` the only file calling the client, `components/`, `model/` pure TypeScript with `*.spec.ts`, `index.ts` the public API); the route component `pages/RolesPage.vue`, the route in `app/router/index.ts`; translations in `shared/i18n/locales/en.json` and `tr.json`. `repository-shared-types.md`: permission keys are the generated `Permission` type, never written as a free string list |
| e2e (`e2e/`) | The existing Playwright tests (`users.e2e.ts`) and screenshots (`e2e/screenshots/*.shot.ts`, `mise run screenshots`) |
| docs | Root `README.md`, section "3. Log in" (the Admin pages) |

## Design

- **Route** `/admin/roles` (name `roles`), `meta.permission: PERMISSIONS.roleManage`.
  `PERMISSIONS` in `features/auth/model/permissions.ts` gets `roleManage: 'role:manage'`.
- **Admin menu** (`app/App.vue`): the dropdown shows when the user holds `user:read` or
  `role:manage`, with **Users** only with `user:read` and **Roles** only with `role:manage`.
  `adminOptions` filters them by `session.can`.
- **`features/roles/`**:
  - `api.ts`: `rolesApi.list()`, `create(body)`, `update(id, body)`, `remove(id)` against
    `/api/roles` (plan 1). The types `Role` (now with `userCount` and `version`),
    `CreateRoleRequest` and `UpdateRoleRequest` come from `shared/api/types.ts`, derived from the
    generated schema.
  - `model/permissionGroups.ts`: `ALL_PERMISSIONS`, every `Permission` key in a fixed order, checked
    to be complete at compile time (a type that fails `vue-tsc` when a generated `Permission` value
    is missing from the list), and `permissionGroups()`, which groups them by area from the key's
    first segment: `analysis`, `preset`, `settings`, `user` (with `role:manage`) and `audit`.
  - `model/roleGuards.ts`: what the backend would refuse, as in `features/users/model/guardRails.ts`:
    `renameBlocked(role)` (built-in), `permissionsLocked(role)` (built-in ADMIN, recognised by its
    ID `ADMIN`), `deleteBlocked(role)` (`built_in_role`, or `role_in_use` when `userCount > 0`).
  - `components/RoleManager.vue`: a table with name (and a "Built-in" tag), description, the
    permissions as tags, the number of users, and the actions **Edit** and **Delete** (with a
    popconfirm). A blocked action is disabled and explains why in a tooltip, as the Users page
    does. **New role** above the table. A `concurrent_update` answer shows the error and reloads.
  - `components/RoleDialog.vue`: one dialog for create and edit: name, description, and the
    permission checklist. For a built-in role the name and description are read-only, and for ADMIN
    the checklist is read-only with a note. It sends only the changed fields with the role's
    `version`.
  - `components/PermissionChecklist.vue`: a checkbox group per area (`NCheckboxGroup`), each
    permission with its key and a short label.
  - `index.ts`: exports `RoleManager`.
- **`pages/RolesPage.vue`**: an `NCard` titled "Roles" with `RoleManager`, like `UsersPage.vue`.
- **i18n** (`en.json`, `tr.json`): `nav.roles`; a `roles` section (title, columns, actions, dialog
  labels, confirmations, guard explanations); a `permissions` section with an area label per group
  and a label per permission key; error labels `role_not_found`, `invalid_role_name`,
  `invalid_role_description`, `role_name_taken`, `built_in_role`, `role_in_use` (with
  `{userCount}`).

## Work packages

### WP1: Roles feature, page and route

- **Depends on**: none (plan 1 is merged)
- **Files**:
  - `artifact/frontend/src/features/roles/api.ts`, `index.ts`, `components/RoleManager.vue`, `components/RoleDialog.vue`, `components/PermissionChecklist.vue`, `model/permissionGroups.ts`, `model/permissionGroups.spec.ts`, `model/roleGuards.ts`, `model/roleGuards.spec.ts` (all new)
  - `artifact/frontend/src/pages/RolesPage.vue` (new)
  - `artifact/frontend/src/app/router/index.ts`, `artifact/frontend/src/app/App.vue`
  - `artifact/frontend/src/features/auth/model/permissions.ts`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [ ] Add the feature, the page and the route; show **Roles** in the Admin menu with
        `role:manage`.
  - [ ] Add the translations in both locales.
- **Tests**:
  - `permissionGroups.spec.ts`: every permission is in exactly one group, the groups come in the
    order analysis, preset, settings, user, audit, and `role:manage` is in the user group.
  - `roleGuards.spec.ts`: a built-in role cannot be renamed or deleted; ADMIN's permissions are
    locked and ANALYST's are not; a custom role with users cannot be deleted, one without can.
  - `permissions.spec.ts` (existing) keeps passing with the new key.

### WP2: End-to-end test and screenshot

- **Depends on**: WP1
- **Files**:
  - `e2e/tests/roles.e2e.ts` (new)
  - `e2e/screenshots/roles.shot.ts` (new), `docs/screenshots/roles.png` (new, taken with `mise run screenshots`)
- **Steps**:
  - [ ] Write the e2e test and the screenshot script.
  - [ ] Take the screenshot.
- **Tests**:
  - `roles.e2e.ts`, as the shared admin, with unique names per run (the tests run in parallel and
    are retried): open **Admin → Roles**, create a role with only `analysis:read`, create a user
    with it through the API, log the user in another context (changing the one-time password),
    and check they see **Analyses** but not **New analysis** or **Settings**. Then the admin adds
    `analysis:run` to the role, and the user's page shows **New analysis** after a reload, without
    logging in again. The role's Delete button is disabled while the user holds it.
  - `roles.shot.ts`: the Roles page with the three built-in roles and one custom role ("Read-only
    analyst", `analysis:read` and `preset:read`, one user), the edit dialog closed.

## Tests

- Vitest: `permissionGroups.spec.ts`, `roleGuards.spec.ts`, the existing specs.
- `vue-tsc` (`mise run check`) proves `ALL_PERMISSIONS` lists every generated `Permission`.
- e2e: `roles.e2e.ts` covers the issue's "a custom role with only `analysis:read` gives exactly that
  access" in the UI, and the no-re-login rule end to end. The backend side of the acceptance
  criteria is proved in plan 1.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, section "3. Log in", after the Admin → Users paragraph | A paragraph on **Admin → Roles**: who sees it (`role:manage`), creating a role and choosing its permissions by area, what cannot be changed on built-in roles, that a role with users cannot be deleted, and that a change applies to logged-in users on their next request |
| Text | `README.md`, the `/api/roles` bullet of plan 1 | One line: the UI's **Admin → Roles** page (`/admin/roles`) uses these endpoints |
| Screenshot | `docs/screenshots/roles.png` (new) | The Roles page as in `roles.shot.ts` (1440×900, dark theme), shown in the new README paragraph |

## Out of scope

- The backend (`/api/roles`, the refresh of session authorities): plan 1.
- Editing a user's roles from the Roles page: the Users page does that.
