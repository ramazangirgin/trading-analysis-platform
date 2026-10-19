# Plan: Custom roles API

- **Issue**: #90 (RBAC: custom roles API and UI)
- **Plan**: 1 of 2 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Admins with `role:manage` can create, rename, change and delete roles beyond ADMIN, ANALYST and
VIEWER through `/api/roles`, and choose their permissions. Built-in roles cannot be renamed or
deleted, and ADMIN's permissions cannot change. A role that is still assigned to users cannot be
deleted. When a role's permissions change, every logged-in user who holds it gets the new
permissions on their next request, without logging in again. The UI follows in plan 2
(`90-custom-roles-ui`).

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend, identity domain (`:backend:domain:identity:core`, `:adapter`) | `backend-java-package-structure.md`: use case in `core.inbound`, service in `core.service` (package-private), errors in `core.exception` as `IdentityError` constants; `backend-java-persistence.md`: Spring Data only, the port's `save` keeps the optimistic lock with `@Version`; `backend-database-naming.md`: a new migration `V6__…` in the identity schema, unique index named `<TABLE>_<COLUMNS>_UK` |
| backend, BFF (`:backend:bff:api`, `:impl`) | `backend-java-rest-api.md`: return the DTO for a `200`, `ResponseEntity` only for `201` / `204`; domain errors go through `IdentityExceptionToApiExceptionMapper` and `DomainExceptionHandler`, never a `try` / `catch` in the delegate; every delegate method has `@RequiresPermission` (`TAP-A1`); security code only in `bff.delegate.impl.config`; mappers are MapStruct `SourceToTargetMapper` with one `map` method in `bff.delegate.impl.mapper` |
| shared types | `repository-shared-types.md`: permissions on the wire are `PermissionDto` keys, mapped with MapStruct; `openapi.json` and `schema.d.ts` regenerated with `mise run api-types` |
| docs | Root `README.md`, section "Login" (backend bullets and the permission table) |

## Design

### Domain (identity core)

- **`ManageRolesUseCase`** (`core.inbound`) replaces `ListRolesUseCase`:
  - `List<RoleSummary> list()`: all roles sorted by name, each with the number of users (enabled or
    not) that hold it.
  - `RoleSummary get(RoleId id)`: `ROLE_NOT_FOUND`.
  - `RoleSummary create(String name, String description, Set<Permission> permissions)`: a custom
    role (`builtIn = false`, `RoleId.newId()`). `INVALID_ROLE_NAME`, `ROLE_NAME_TAKEN`.
  - `RoleSummary update(RoleId id, Optional<String> name, Optional<String> description,
    Optional<Set<Permission>> permissions, Long expectedVersion)`: an empty argument leaves its part
    as it is. An update that changes nothing writes nothing. Errors: `ROLE_NOT_FOUND`,
    `INVALID_ROLE_NAME`, `ROLE_NAME_TAKEN`, `BUILT_IN_ROLE`, `CONCURRENT_UPDATE`.
  - `void delete(RoleId id)`: `ROLE_NOT_FOUND`, `BUILT_IN_ROLE`, `ROLE_IN_USE`.
- **`RoleSummary`** (`core.inbound`, a record next to the use case, like `UserAccount`):
  `Role role, long userCount`.
- **`RoleManagementService`** (`core.service`) implements it and replaces `RoleQueryService`. The
  rules:
  - Name: trimmed, 2 to 40 ASCII characters of letters, digits, space, `_` and `-` (the same
    reason as `Username`: case-insensitive uniqueness must not depend on the database's locale). It
    must be unique ignoring case, so a custom `admin` cannot sit next to `ADMIN`. A name taken
    (checked with `findByName`, and backed by the unique index, whose `DuplicateKeyException` is
    turned into the same error) is `ROLE_NAME_TAKEN` (params `name`). An invalid one is
    `INVALID_ROLE_NAME`.
  - Description: trimmed, at most 200 characters, empty allowed. A longer one is
    `INVALID_ROLE_DESCRIPTION`.
  - Permissions: any subset of `Permission`, empty allowed (a role can be prepared before it grants
    anything).
  - **Built-in roles**: their name and description cannot change, and they cannot be deleted
    (`BUILT_IN_ROLE`, params `id` and `change`: `rename`, `description`, `permissions`, `delete`).
    ANALYST's and VIEWER's permissions can change. ADMIN's cannot (`BUILT_IN_ROLE`, change
    `permissions`), so the platform always has a role with every permission, which the last-admin
    guard of #88 relies on. A PATCH that sends the current name or description of a built-in role
    unchanged is not a change and passes.
  - **Delete**: refused with `ROLE_IN_USE` (params `id`, `userCount`) while any user, enabled or
    disabled, holds the role. The service checks the count first. `USER_ROLES_ROLE_ID_FK` backs it
    when an admin assigns the role at the same moment: the port's delete then fails with Spring's
    `DataIntegrityViolationException`, which the service turns into `ROLE_IN_USE` as well.
  - Optimistic lock: as in `UserManagementService`, a stale `expectedVersion` or an
    `OptimisticLockingFailureException` from `save` is `CONCURRENT_UPDATE`. Its message names the
    role (`Role <id> was changed by someone else`).
  - Every change is logged at `info` (created, renamed, permissions changed to …, deleted), like
    the user changes.
- **`IdentityError`** gets `ROLE_NOT_FOUND`, `INVALID_ROLE_NAME`, `INVALID_ROLE_DESCRIPTION`,
  `ROLE_NAME_TAKEN`, `BUILT_IN_ROLE` and `ROLE_IN_USE`.
- **`Role`** gets `withName(String)` and `withDescription(String)` next to `withPermissions`.
- **Ports**:
  - `RoleRepositoryPort`: `findByName` ignores case (its Javadoc says so); new
    `void deleteById(RoleId id)`.
  - `UserRepositoryPort`: new `Map<RoleId, Long> countUsersByRole()` (every user, enabled or not;
    roles without users are missing from the map) for the list, and `long countWithRole(RoleId)`
    for get and delete.

### Persistence (identity adapter)

- Migration `V6__role_name_unique_ignoring_case.sql`: drops `ROLES_NAME_UK` and creates
  `CREATE UNIQUE INDEX "ROLES_NAME_LOWER_UK" ON "IDENTITY"."ROLES" (LOWER("NAME"))`, with the
  header comment the other migrations have (why: names are unique ignoring case, like usernames).
- `RoleJpaRepository.findByName` becomes a `lower(...) = lower(:name)` query, like
  `UserJpaRepository.findByUsernameIgnoringCase`.
- `JpaRoleRepositoryAdapter.deleteById`: `repository.deleteById` and a flush in one transaction, so
  the foreign key fails inside the call.
- `UserJpaRepository`: `countWithRole` (JPQL `count` over `u.roleIds`) and a grouped count
  (`select r, count(u) … group by r`) behind `countUsersByRole`.

### BFF

Endpoints in `RolesApiController` / `RolesApiDelegate` / `RolesApiDelegateImpl`:

| Endpoint | Permission | Success | Errors |
|---|---|---|---|
| `GET /api/roles` | `user:read` or `role:manage` (unchanged) | 200 `List<RoleDto>` | |
| `GET /api/roles/{id}` | `user:read` or `role:manage` | 200 `RoleDto` | 404 `role_not_found` |
| `POST /api/roles` | `role:manage` | 201 `ResponseEntity<RoleDto>` with `Location: /api/roles/{id}` | 400 `invalid_role_name`, `invalid_role_description`, `invalid_request` (validation, an unknown permission key); 409 `role_name_taken` |
| `PATCH /api/roles/{id}` | `role:manage` | 200 `RoleDto` | 400 as above, and `invalid_request` when none of `name`, `description`, `permissions` is given (an `ApiException` in the delegate, as in `updateUser`); 404; 409 `built_in_role`, `role_name_taken`, `concurrent_update` |
| `DELETE /api/roles/{id}` | `role:manage` | 204 `ResponseEntity<Void>` | 404; 409 `built_in_role`, `role_in_use` (`params.userCount`) |

- DTOs in `bff.controller.api.model`: `RoleDto` gets `userCount` and `version`.
  `CreateRoleRequest(@NotBlank String name, String description, @NotNull List<@NotNull PermissionDto> permissions)`
  and `UpdateRoleRequest(String name, String description, List<@NotNull PermissionDto> permissions, Long version)`.
  A permission key that is not a `PermissionDto` value is an unreadable body: 400 from
  `ApiExceptionHandler`.
- Mappers in `bff.delegate.impl.mapper`: `RoleSummaryToRoleDtoMapper` replaces
  `RoleToRoleDtoMapper` (permissions sorted by key, as now), and a new
  `PermissionDtoToPermissionMapper` (MapStruct enum mapping, the reverse of
  `PermissionToPermissionDtoMapper`).
- `IdentityExceptionToApiExceptionMapper`: `ROLE_NOT_FOUND` 404; `INVALID_ROLE_NAME`,
  `INVALID_ROLE_DESCRIPTION` 400; `ROLE_NAME_TAKEN`, `BUILT_IN_ROLE`, `ROLE_IN_USE` 409. No new
  handler method: `IdentityException` is handled already.

### Permission changes take effect on the next request

A session keeps the authorities of its login today (`AuthApiDelegateImpl.authorities`). Ending the
sessions of every holder of a changed role would force them to log in again, which the issue rules
out. Instead, sessions refresh their authorities when roles have changed since they were built:

- `SessionAuthorities` (`bff.delegate.impl.config`, a `@Component`): holds a generation counter
  (`AtomicLong`, in memory like the sessions: the platform runs as one instance). It has
  `rolesChanged()` (adds 1), `authorities(AuthenticatedUser)` (moved from `AuthApiDelegateImpl`:
  `ROLE_<name>`, the permission keys, and `PasswordChangeRequiredFilter.AUTHORITY` when the
  password must be changed), and stamps a session with the generation its authorities were built
  at (a session attribute).
- `AuthApiDelegateImpl.startSession` builds the authorities through it and stamps the session.
- `RoleAuthoritiesRefreshFilter` (`bff.delegate.impl.config`, a `OncePerRequestFilter`, added in
  `SecurityConfig` before `AuthorizationFilter`, so `PasswordChangeRequiredFilter` and the method
  security see the new authorities): for an `/api/**` request with an authenticated session whose
  stamp is older than the generation (or missing), it loads the user with
  `AuthenticateUseCase.findActiveUser`, replaces the authentication in the security context with
  the same principal and the new authorities, saves the context through the shared
  `SecurityContextRepository`, and stamps the session. A user that is no longer active is left as
  it is: disabling already ends their sessions (#88).
- `RolesApiDelegateImpl.updateRole` calls `sessionAuthorities.rolesChanged()` after a successful
  update. Create and delete need none: a new role has no holders and an assigned role cannot be
  deleted.
- An open event stream is not a request, so a stream that is open when its user loses
  `analysis:read` keeps running until it ends or its session ends. This is written in the README.

The cost is one user lookup per session after a role change, not per request: the check itself is
a comparison of two numbers.

## Work packages

### WP1: Identity domain and persistence

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/…/identity/core/inbound/ManageRolesUseCase.java` (new), `RoleSummary.java` (new), `ListRolesUseCase.java` (removed)
  - `…/identity/core/service/RoleManagementService.java` (new), `RoleQueryService.java` (removed)
  - `…/identity/core/model/Role.java`
  - `…/identity/core/exception/IdentityError.java`
  - `…/identity/core/outbound/persistence/RoleRepositoryPort.java`, `UserRepositoryPort.java`
  - `artifact/backend/domain/identity/adapter/src/main/java/…/identity/adapter/persistence/JpaRoleRepositoryAdapter.java`, `RoleJpaRepository.java`, `JpaUserRepositoryAdapter.java`, `UserJpaRepository.java`
  - `artifact/backend/domain/identity/adapter/src/main/resources/…/persistence/migration/V6__role_name_unique_ignoring_case.sql` (new)
  - Tests: `…/core/service/RoleManagementServiceTest.java` (new), `RoleQueryServiceTest.java` (removed), `…/core/model/RoleTest.java`; the fake repositories in `UserManagementServiceTest`, `AuthenticationServiceTest`, `PasswordChangeServiceTest` (new port methods); `…/adapter/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [x] Add the use case, `RoleSummary`, the error codes and `Role.withName` / `withDescription`.
  - [x] Implement `RoleManagementService` with the rules of the design; remove `RoleQueryService`
        and `ListRolesUseCase`.
  - [x] Extend the ports and the JPA adapters; write the `V6` migration.
- **Tests**:
  - `RoleManagementServiceTest`: list with user counts and sorted by name; create (valid, invalid
    name, too long description, name taken in another case, `DuplicateKeyException` from the port);
    update of a custom role (rename, description, permissions, nothing changed writes nothing, stale
    version, `OptimisticLockingFailureException`); built-in protections (renaming or changing the
    description of ANALYST, changing ADMIN's permissions are `BUILT_IN_ROLE`; changing ANALYST's
    and VIEWER's permissions works; sending a built-in's current name passes); delete (custom role
    without users; built-in is `BUILT_IN_ROLE`; held by an enabled or a disabled user is
    `ROLE_IN_USE` with `userCount`; `DataIntegrityViolationException` from the port is
    `ROLE_IN_USE`); `ROLE_NOT_FOUND` on get, update and delete.
  - `JpaIdentityRepositoryTest` (PostgreSQL): `findByName` ignores case; a second role whose name
    differs only in case fails with `DuplicateKeyException`; `deleteById` of an assigned role fails
    with `DataIntegrityViolationException` and keeps the row; `countWithRole` and
    `countUsersByRole` count enabled and disabled users. `IdentityPersistenceConventionsTest` passes
    with the new index name.

### WP2: Roles REST API

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/bff/api/src/main/java/…/bff/controller/api/RolesApiController.java`, `RolesApiDelegate.java`
  - `…/bff/controller/api/model/RoleDto.java`, `CreateRoleRequest.java` (new), `UpdateRoleRequest.java` (new)
  - `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/RolesApiDelegateImpl.java`
  - `…/bff/delegate/impl/mapper/RoleSummaryToRoleDtoMapper.java` (new), `RoleToRoleDtoMapper.java` (removed), `PermissionDtoToPermissionMapper.java` (new)
  - `…/bff/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapper.java`
  - Tests: `…/bff/delegate/impl/mapper/RoleSummaryToRoleDtoMapperTest.java` (replaces `RoleToRoleDtoMapperTest.java`)
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated with `mise run api-types`)
  - `artifact/frontend/src/shared/api/types.ts` (export `CreateRoleRequest` and `UpdateRoleRequest`; `Role` gains the new fields from the schema)
- **Steps**:
  - [x] Add the endpoints, DTOs and mappers of the table above.
  - [x] Map the new error codes.
  - [x] Regenerate `openapi.json` and `schema.d.ts`; `mise run check` must pass (the frontend
        type-check sees the new `RoleDto` fields).
- **Tests**: `RoleSummaryToRoleDtoMapperTest` (id, name, built-in, description, permissions sorted
  by key, user count, version). The endpoints are covered by the integration tests of WP4.

### WP3: Session authorities follow role changes

- **Status**: done
- **Depends on**: WP2 (the delegate calls it)
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/config/SessionAuthorities.java` (new), `RoleAuthoritiesRefreshFilter.java` (new), `SecurityConfig.java`
  - `…/bff/delegate/impl/AuthApiDelegateImpl.java`, `RolesApiDelegateImpl.java`
  - Tests: `artifact/backend/bff/impl/src/test/java/…/bff/delegate/impl/config/SessionAuthoritiesTest.java` (new), `RoleAuthoritiesRefreshFilterTest.java` (new)
- **Steps**:
  - [x] Move the authority building from `AuthApiDelegateImpl` to `SessionAuthorities`; stamp the
        session at login and at a password change.
  - [x] Add the filter to the chain before `AuthorizationFilter`.
  - [x] Call `rolesChanged()` after a successful role update.
- **Tests**:
  - `SessionAuthoritiesTest`: the authorities of a user (roles, permission keys, the password
    mark); a session stamped before `rolesChanged()` is stale, one stamped after is not, one
    without a stamp is stale.
  - `RoleAuthoritiesRefreshFilterTest` (mocked `AuthenticateUseCase`, a mock request with a
    session): a stale session gets the user's current authorities and a new stamp, and the context
    is saved; a fresh session is not looked up; an inactive user keeps the old authentication; a
    request without an authentication or outside `/api/` passes untouched.

### WP4: Integration tests

- **Status**: done
- **Depends on**: WP2, WP3
- **Files**:
  - `artifact/backend/src/test/java/…/platform/RolesApiIntegrationTest.java` (new, extends `UsersApiTestBase`)
  - `…/platform/EndpointPermissionsIntegrationTest.java`
  - `…/platform/UsersApiTestBase.java` (helpers to create a role, when needed)
- **Steps**:
  - [x] Write the tests below.
- **Tests**:
  - `RolesApiIntegrationTest`:
    - permission checks: a user with `user:read` only lists and gets roles but gets 403 on POST,
      PATCH and DELETE; a user with a custom role holding only `role:manage` does all of them;
    - create, get, rename, change permissions, delete a custom role (201 with `Location`, 200, 204),
      with the error answers: 409 `role_name_taken` (also in another case), 400
      `invalid_role_name`, 400 `invalid_request` for an empty PATCH and for an unknown permission
      key, 404 `role_not_found`, 409 `concurrent_update` with a stale `version`;
    - **built-in protections**: renaming ADMIN, ANALYST or VIEWER, changing ADMIN's permissions and
      deleting any built-in role answer 409 `built_in_role`; changing VIEWER's permissions works;
    - **an assigned role cannot be deleted**: 409 `role_in_use` with `params.userCount` while a
      user (also a disabled one) holds it; after the role is removed from the user
      (`PATCH /api/users/{id}`), the delete answers 204;
    - **no re-login**: a user logged in with a custom role gets 403 on `GET /api/presets`; an admin
      adds `preset:read` to the role; the same session's next `GET /api/presets` answers 200, and
      `GET /api/auth/me` lists the new permission. Removing it again gives 403 on the next request.
  - `EndpointPermissionsIntegrationTest`: a new test proves the acceptance criterion "a custom role
    with only `analysis:read` gives exactly that access": a user whose only role is a custom role
    with `analysis:read` is sent every `/api` endpoint, and the ones not refused (not 403) are
    exactly the open routes and the `analysis:read` endpoints of the README table.

## Tests

- Unit: `RoleManagementServiceTest`, `RoleTest`, `RoleSummaryToRoleDtoMapperTest`,
  `SessionAuthoritiesTest`, `RoleAuthoritiesRefreshFilterTest`.
- Persistence (PostgreSQL, Testcontainers): `JpaIdentityRepositoryTest`,
  `IdentityPersistenceConventionsTest`.
- Integration (`:backend:test`): `RolesApiIntegrationTest`, `EndpointPermissionsIntegrationTest`;
  `UsersApiIntegrationTest`, `RolePermissionsIntegrationTest` and `UserLifecycleIntegrationTest`
  keep passing (the user forms still read `GET /api/roles`).
- The issue's "done when": the custom `analysis:read` role test in
  `EndpointPermissionsIntegrationTest`, and the built-in and assigned-role tests in
  `RolesApiIntegrationTest`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, section "Login", the backend bullets | A bullet for `/api/roles`, in the style of the `/api/users` one: the `RoleDto` fields (now with `userCount` and `version`), each endpoint with its answers and error codes, the name rules, the built-in protections, the assigned-role rule, and that a permission change applies to the holders' next request without a new login (an open event stream keeps running until it or its session ends). Replace the `GET /api/roles` sub-bullet under `/api/users`. |
| Text | `README.md`, the permission table | `GET /api/roles`, `GET /api/roles/{id}`: `user:read` or `role:manage`; `POST`, `PATCH` and `DELETE /api/roles…`: `role:manage` |
| Text | `README.md`, the paragraph "Three roles are built in" | Admins can add custom roles with any set of permissions; ANALYST's and VIEWER's permissions can be changed, ADMIN's cannot, and no built-in role can be renamed or deleted |
| API | `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | Regenerated (`mise run api-types`) |
| Screenshot | none | No page changes in this plan; the Roles page and its screenshot come with plan 2 |

## Out of scope

- The **Admin → Roles** page, its e2e test and screenshot: plan 2 (`90-custom-roles-ui`).
- Changing a **user's** roles (`PATCH /api/users/{id}`) still ends that user's sessions, as #88
  documented. Switching it to the refresh of this plan would change documented behaviour; it can be
  a follow-up issue.
- Closing open event streams when a role loses `analysis:read`.
- Audit log entries for role changes: #93.

## Open questions

None.
