# Plan: User management API

- **Issue**: #88 (User management API)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md), 4.9.0 → 4.10.0

## Goal

Admins manage accounts through the REST API. They can list and read users, create a user with a
one-time password (entered or generated), change a user's roles, disable or enable them, reset
their password and unlock them. A disabled user's sessions end at once, and so do the sessions of a
user whose password is reset. Guard rails keep at least one enabled ADMIN and stop an admin from
locking themselves out. There is no hard delete. The API also gives what the admin UI (#89) needs
to show: each user's roles by name, status and last login, plus the list of roles to choose from.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend, identity core | `backend-java-package-structure.md`: use cases in `domain.identity.core.inbound` (with the types they hand out), services package-private in `…core.service`, errors in `…core.exception` (`IdentityError`), port changes in `…core.outbound.persistence`. `backend-java-persistence.md`: the core sets domain-fact timestamps from the injected `Clock`; services turn `OptimisticLockingFailureException` into `CONCURRENT_UPDATE`; the version travels through the core |
| backend, identity adapter | `backend-database-naming.md`: a new migration `V5__…` (never edit V1–V4), uppercase quoted names, a comment for every column that isn't obvious. `backend-java-persistence.md`: JPA/JPQL only (no native query), index-friendly lookups, `@Transactional(readOnly = true)` reads, MapStruct mappers in `adapter.persistence.mapper` |
| backend, BFF | `backend-java-package-structure.md`: controllers and delegate interfaces in `bff.controller.api`, DTOs in `…api.model`, delegate impls in `bff.delegate.impl`, MapStruct mappers in `…impl.mapper`, error mapping in `…impl.mapper.error`, Spring Security code (sessions) in `…impl.config`. Every delegate method carries `@RequiresPermission` (Checkstyle `TAP-A1`, `EndpointPermissionsIntegrationTest`) |
| shared types | `repository-shared-types.md`: the frontend gets the new API shapes only through `openapi.json` and `mise run api-types` (`shared/api/schema.d.ts`), never hand-written |
| docs | README "Development → Login" section, `backend-java-persistence.md` (timestamps table, `CONCURRENT_UPDATE` for identity) |

## Design

### Endpoints (BFF)

`UsersApiController` (`/api/users`, it already exists with `unlock`) and a new `RolesApiController`
(`/api/roles`), each with its delegate interface and `*ApiDelegateImpl`:

| Endpoint | Permission | Body → answer |
|---|---|---|
| `GET /api/users` | `user:read` | 200 `UserDto[]`, sorted by username, ignoring case (`findAll` already orders them) |
| `GET /api/users/{id}` | `user:read` | 200 `UserDto`; 404 `user_not_found` |
| `POST /api/users` | `user:manage` | `CreateUserRequest` → 201 `CreatedUserDto`; 400 `invalid_username`, `weak_password`, `unknown_role`, `invalid_request`; 409 `username_taken` |
| `PATCH /api/users/{id}` | `user:manage` | `UpdateUserRequest` → 200 `UserDto`; 400 `unknown_role`, `invalid_request` (neither `roleIds` nor `enabled` given); 404; 409 `cannot_disable_self`, `cannot_remove_own_admin`, `last_admin`, `concurrent_update` |
| `POST /api/users/{id}/password-reset` | `user:manage` | `ResetPasswordRequest` → 200 `OneTimePasswordDto`; 400 `weak_password`; 404 |
| `POST /api/users/{id}/unlock` | `user:manage` | unchanged: 204, 404 |
| `GET /api/roles` | `user:read` or `role:manage` | 200 `RoleDto[]` sorted by name: what the user forms of #89 offer. #90 adds the writes, with `role:manage` |

DTOs in `bff.controller.api.model`:

- `UserDto(String id, String username, List<UserRoleDto> roles, boolean enabled, boolean mustChangePassword, boolean locked, Instant lockedUntil, Instant lastLoginAt, Instant createdAt, Instant updatedAt, Long version)`.
  The roles are sorted by name. `lockedUntil` and `lastLoginAt` are null when unset. `locked` is
  `isLockedAt(now)`: an expired lock reads as not locked.
- `UserRoleDto(String id, String name)`.
- `CreateUserRequest(@NotBlank String username, @NotNull List<String> roleIds, String password)`:
  a blank or missing `password` means "generate one".
- `CreatedUserDto(UserDto user, String oneTimePassword)`: `oneTimePassword` is set only when it was
  generated, so the admin sees it once. It is null when the admin entered the password.
- `UpdateUserRequest(List<String> roleIds, Boolean enabled, Long version)`: null fields stay
  unchanged. `version` is optional, as in `SavePresetRequest`: without it, the write applies to
  what is stored.
- `ResetPasswordRequest(String password)`: blank or missing means "generate one".
- `OneTimePasswordDto(String oneTimePassword)`: the generated password, or null when it was
  entered.
- `RoleDto(String id, String name, boolean builtIn, String description, List<PermissionDto> permissions)`.
  The permissions are sorted by key and mapped with the existing `PermissionToPermissionDtoMapper`.

The delegates take the caller's user ID from the security context: the principal is the user ID
string, the same way as `AuthApiDelegateImpl#currentUserId`. They call the use cases, map the
results with MapStruct mappers (`UserAccountToUserDtoMapper`, `RoleToRoleDtoMapper`) and map
`IdentityException` with `IdentityExceptionToApiExceptionMapper`.

### Ending sessions

A new component `UserSessions` in `bff.delegate.impl.config` (it is Spring Security code) wraps the
`SessionRegistry` and `SessionEventStreams`:

- `endAll(UserId)`: for every session of the user, close its event streams
  (`SessionEventStreams#close`), then `expireNow()`.
- `endAllExcept(UserId, String sessionId)`: the same, but keep one session.

The next request of an expired session gets 401 "Session ended" from the concurrency filter that is
already configured. `AuthApiDelegateImpl#changePassword` changes to use `endAllExcept` instead of its
inline loop, with no change in behaviour.

When the delegates end sessions:

- **Disable** (`PATCH` with `enabled: false`): `endAll(target)`. The target is never the caller,
  because of the guard rail.
- **Password reset**: `endAll(target)`. When admins reset their own password, their current session
  ends too, and they log in again with the one-time password.
- **Role change** (`PATCH` with `roleIds` that differ from the stored ones): the session keeps the
  authorities from the login, so a demoted user would keep their old permissions. So
  `endAllExcept(target, the caller's session ID)`. When admins change their own roles, they keep
  their current session with its old authorities until the next login. They cannot remove their own
  ADMIN, so they cannot lose access this way.

This fulfils the plan the Javadoc of `PasswordChangeRequiredFilter` already describes: a reset
flags the user and logs them out, so the flagged user always logs in again and gets the mark.

### Identity core

- **`ManageUsersUseCase`** (`…core.inbound`), implemented by **`UserManagementService`**
  (`…core.service`, package-private):
  - `List<UserAccount> list()`, `UserAccount get(UserId)`;
  - `CreatedUserAccount create(String username, Set<RoleId> roleIds, Optional<String> password)`;
  - `UserAccount update(UserId actor, UserId target, Optional<Set<RoleId>> roleIds, Optional<Boolean> enabled, Long expectedVersion)`;
  - `Optional<String> resetPassword(UserId target, Optional<String> password)`, which returns the
    generated password, if there is one.
- **`ListRolesUseCase`** (`List<Role> list()`), implemented by **`RoleQueryService`**.
- **Types the use cases hand out** (`…core.inbound`):
  - `UserAccount(UserId id, Username username, List<Role> roles, boolean enabled, boolean mustChangePassword, boolean locked, Optional<Instant> lockedUntil, Optional<Instant> lastLoginAt, Instant createdAt, Instant updatedAt, Long version)`;
  - `CreatedUserAccount(UserAccount account, Optional<String> generatedPassword)`.
- **Rules**:
  - **Create**:
    - The username must be valid (`Username`, `INVALID_USERNAME`). Every role ID must exist
      (`UNKNOWN_ROLE`, with the ID in `params.roleId`).
    - An entered password must pass `PasswordPolicy#check` (`WEAK_PASSWORD`).
    - A generated one comes from `OneTimePasswordGenerator`, with length
      `max(24, policy.minLength())`. The 24 becomes a constant shared with `FirstAdminService`.
    - The user is enabled, `mustChangePassword = true`, with no lock and no failed logins.
    - A taken username (Spring's `DuplicateKeyException` from the port) becomes `USERNAME_TAKEN`.
  - **Update**:
    - The role IDs must exist (`UNKNOWN_ROLE`).
    - The actor cannot disable themselves (`CANNOT_DISABLE_SELF`) or remove `RoleId.ADMIN` from
      themselves (`CANNOT_REMOVE_OWN_ADMIN`).
    - A change that would disable or demote an enabled ADMIN, when no other enabled ADMIN exists,
      fails with `LAST_ADMIN`. The count comes from the new port method `countEnabledWithRole`, minus
      the target.
    - An update that changes nothing writes nothing.
    - `OptimisticLockingFailureException` becomes `CONCURRENT_UPDATE`, with `params.id`.
  - **Reset**: an entered password is checked against the policy. The reset sets the new hash and
    `mustChangePassword = true`, and clears the lockout and the failed-login count, because the admin
    has just given the user a fresh credential. It does not enable a disabled user.
  - **Unknown user**: `USER_NOT_FOUND` (with `params.id`), as `UserLockService` already does.
- **The last-admin race**: two admins who disable each other at the same moment would each see the
  other as "another enabled ADMIN", and the platform would end up with none. Every update that
  touches `enabled` or roles runs its check and its write under one `ReentrantLock` in
  `UserManagementService`. That is enough because the platform runs as one instance: the sessions
  and the login-attempt counts already live in memory. The Javadoc says so.
- **The service logs** creates, role changes, disables, enables and resets at INFO: the user ID and
  what changed. It never logs a password. The audit log is #93.
- **`IdentityError`** gets `USERNAME_TAKEN`, `UNKNOWN_ROLE`, `CANNOT_DISABLE_SELF`,
  `CANNOT_REMOVE_OWN_ADMIN`, `LAST_ADMIN` and `CONCURRENT_UPDATE`.
  `IdentityExceptionToApiExceptionMapper` maps them:
  - 400: `INVALID_USERNAME` (it can now come from user input, so 400 replaces 500), `UNKNOWN_ROLE`;
  - 409: `USERNAME_TAKEN`, `CANNOT_DISABLE_SELF`, `CANNOT_REMOVE_OWN_ADMIN`, `LAST_ADMIN`,
    `CONCURRENT_UPDATE`.

  Its comment changes to match.
- **Last login**:
  - `User` gets `Optional<Instant> lastLoginAt`, a domain fact set from the `Clock`.
  - `AuthenticationService#recordSuccess` sets it on every successful login, so a login now always
    writes the user row, in the same save that resets the count or rehashes.
  - Every `new User(...)` call site changes, in main and test code (`FirstAdminService`,
    `TestSession`, `LoginLockoutIntegrationTest` and the core's fakes), and so do the `with…` methods.
- **`UserRepositoryPort`** gets `long countEnabledWithRole(RoleId role)`.

### Identity adapter

- **`V5__add_last_login.sql`**: `ALTER TABLE "IDENTITY"."USERS" ADD COLUMN "LAST_LOGIN_AT" TIMESTAMPTZ;`
  with a comment ("when the user last logged in successfully; null until then").
- **`UserEntity`** gets `lastLoginAt`. `UserEntityToUserMapper` and `UserToUserEntityMapper` map it
  with the existing `InstantToOptionalInstantMapper` and `OptionalInstantToInstantMapper`.
  `JpaUserRepositoryAdapter#save` copies it onto the managed entity.
- **`UserJpaRepository`** gets a JPQL count over the role collection:
  `select count(u) from UserEntity u join u.roleIds r where u.enabled = true and r = :roleId`, or the
  equivalent for the element-collection mapping. `JpaUserRepositoryAdapter#countEnabledWithRole`
  calls it, `@Transactional(readOnly = true)`.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Identity core: user management use cases, guard rails, last login

- **Status**: done
- **Depends on**: none
- **Files**:
  - `domain/identity/core/src/main/java/…/core/inbound/ManageUsersUseCase.java`, `ListRolesUseCase.java`, `UserAccount.java`, `CreatedUserAccount.java` (new)
  - `…/core/service/UserManagementService.java`, `RoleQueryService.java` (new)
  - `…/core/service/OneTimePasswordGenerator.java`, `FirstAdminService.java`, `AuthenticationService.java` (changed)
  - `…/core/model/User.java` (`lastLoginAt`)
  - `…/core/exception/IdentityError.java`
  - `…/core/outbound/persistence/UserRepositoryPort.java` (`countEnabledWithRole`)
  - tests under `domain/identity/core/src/test/java/…/core/` (new and changed, including the fakes' `new User(...)` and `countEnabledWithRole`)
- **Steps**:
  - [x] Add `lastLoginAt` to `User`, with a `withLoginSucceeded(Instant at)` that also sets it (or a `withLastLogin`), and update every constructor call in the core
  - [x] Set it in `AuthenticationService#recordSuccess` from the `Clock`
  - [x] Add the new `IdentityError` constants and the port method
  - [x] Share the generated-password length between `FirstAdminService` and the new service
  - [x] Implement `ManageUsersUseCase` in `UserManagementService` with the rules and the lock from the design, and `ListRolesUseCase` in `RoleQueryService`
- **Tests**:
  - `UserManagementServiceTest` (new), with fake ports and a fixed clock:
    - create with an entered password (stored hashed, flagged, the hash matches) and with a
      generated one (returned, length, flagged);
    - a weak entered password, an invalid username, an unknown role, a taken username;
    - update: roles changed, disable, enable, a no-op writes nothing, an unknown role, an unknown
      user;
    - each guard rail: self-disable, self ADMIN removal, the last enabled ADMIN disabled and the last
      one demoted. Another enabled ADMIN lets both through, and a disabled ADMIN does not count;
    - a version conflict becomes `CONCURRENT_UPDATE`;
    - reset with an entered or a generated password sets the flag and clears the lock, and a weak
      one fails;
    - `get` and `list` give the role names and `locked` computed from the clock (an expired lock
      reads as not locked).
  - `AuthenticationServiceTest`: a successful login sets `lastLoginAt` to the clock's instant, and
    a failed one does not.
  - `RoleQueryServiceTest`: lists the roles.
  - `FirstAdminServiceTest` still passes with the shared length.

### WP2: Identity adapter: last-login column and the enabled-admin count

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `domain/identity/adapter/src/main/resources/…/persistence/migration/V5__add_last_login.sql` (new)
  - `…/adapter/persistence/entity/UserEntity.java`
  - `…/adapter/persistence/mapper/UserEntityToUserMapper.java`, `UserToUserEntityMapper.java`
  - `…/adapter/persistence/UserJpaRepository.java`, `JpaUserRepositoryAdapter.java`
  - `domain/identity/adapter/src/test/java/…/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [x] Add the migration with its column comment
  - [x] Map the column in the entity and both mappers, and copy it in `save`
  - [x] Add the JPQL count and `countEnabledWithRole`
- **Tests**:
  - `JpaIdentityRepositoryTest`: `lastLoginAt` round-trips (null and set);
    `countEnabledWithRole` counts only enabled users with that role (a disabled ADMIN and an enabled
    ANALYST are not counted).
  - `IdentityPersistenceConventionsTest` passes unchanged: it checks the new migration's names.

### WP3: BFF: users and roles endpoints, session ending

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `bff/api/src/main/java/…/controller/api/UsersApiController.java`, `UsersApiDelegate.java` (changed)
  - `…/controller/api/RolesApiController.java`, `RolesApiDelegate.java` (new)
  - `…/controller/api/model/UserDto.java`, `UserRoleDto.java`, `CreateUserRequest.java`, `CreatedUserDto.java`, `UpdateUserRequest.java`, `ResetPasswordRequest.java`, `OneTimePasswordDto.java`, `RoleDto.java` (new)
  - `bff/impl/src/main/java/…/delegate/impl/UsersApiDelegateImpl.java` (changed), `RolesApiDelegateImpl.java` (new)
  - `…/delegate/impl/AuthApiDelegateImpl.java` (uses `UserSessions`)
  - `…/delegate/impl/config/UserSessions.java` (new)
  - `…/delegate/impl/mapper/UserAccountToUserDtoMapper.java`, `RoleToRoleDtoMapper.java` (new)
  - `…/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapper.java`
  - `bff/impl/src/test/java/…/mapper/error/IdentityExceptionToApiExceptionMapperTest.java`
  - `bff/impl/src/test/java/…/config/UserSessionsTest.java` (new)
- **Steps**:
  - [x] Add the DTOs, with Javadoc in the style of `PresetDto`
  - [x] Add the controller methods (`consumes` and `produces` JSON, `@Valid @RequestBody`) and the `RolesApiController`
  - [x] Add `UserSessions` and switch `AuthApiDelegateImpl#changePassword` to it
  - [x] Implement the delegates with `@RequiresPermission` per the table, ending sessions as the design says
  - [x] Map the new error codes
- **Tests**:
  - `IdentityExceptionToApiExceptionMapperTest` covers every new code and the new 400 for
    `INVALID_USERNAME`.
  - `UserSessionsTest` (new) proves that `endAll` expires every session of the user and closes its
    streams, and that `endAllExcept` keeps the named one.

### WP4: Integration tests, API types and docs

- **Depends on**: WP2, WP3
- **Files**:
  - `artifact/backend/src/test/java/…/UsersApiIntegrationTest.java` (new)
  - `…/TestSession.java`, `…/LoginLockoutIntegrationTest.java` (`new User(...)`, `validBody`)
  - `…/EndpointPermissionsIntegrationTest.java` (only if it needs changes)
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts`
  - `README.md`, `docs/coding-convention/backend-java-persistence.md`
  - `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py` (`mise run version:bump minor`)
- **Steps**:
  - [ ] Extend `TestSession.validBody` with valid bodies for `POST /api/users`, `PATCH /api/users/{id}` and `…/password-reset`, so the permission check, not validation, answers for a user without roles
  - [ ] Write `UsersApiIntegrationTest` (below)
  - [ ] Regenerate `openapi.json` and `schema.d.ts` (`mise run api-types` against a running backend)
  - [ ] Update the docs (see "Docs to update") and bump the version
- **Tests**: `UsersApiIntegrationTest` (Spring Boot, random port, `TestDatabaseConfiguration`, as
  `LoginLockoutIntegrationTest`):
  - Every endpoint answers 401 without a session.
  - A user without roles gets 403. A user with a test role holding only `user:read` (saved with
    `RoleRepositoryPort`) can call `GET /api/users`, `GET /api/users/{id}` and `GET /api/roles`, but
    gets 403 on `POST`, `PATCH`, `password-reset` and `unlock`. An ADMIN can do everything.
  - Create with an entered password: 201, the user is flagged. The new user logs in with it, gets
    403 `password_change_required` on other routes, and changes the password. Create with a
    generated password: the returned `oneTimePassword` logs in.
  - Create errors: 400 `weak_password` (with reasons), `invalid_username`, `unknown_role`; 409
    `username_taken` (another case of an existing name).
  - List and get: fields, role names, `locked` for a locked user, `lastLoginAt` after a login. 404
    for an unknown ID.
  - PATCH: roles changed. Disable: the user's open session gets 401 on its very next request, its
    open event stream is closed (as in the password-change and stream tests), and a new login is
    401 `bad_credentials`. Enable: the user logs in again. An empty patch is 400 and a stale
    `version` is 409 `concurrent_update`.
  - Guard rails: 409 `cannot_disable_self` and `cannot_remove_own_admin` for the calling admin. 409
    `last_admin` when the target is the only enabled ADMIN: the caller is a test user with a role
    holding `user:manage` but not ADMIN. With a second enabled ADMIN, the same change succeeds.
  - A role change ends the target's sessions (401 on the next request) and keeps the caller's own
    when admins change their own roles.
  - Password reset: entered and generated. The user's open session gets 401, the old password fails,
    the new one logs in flagged, and a locked user is unlocked by the reset.
  - `GET /api/roles` lists ADMIN, ANALYST and VIEWER with their permissions.
  - `EndpointPermissionsIntegrationTest` passes, with the new endpoints included automatically.

## Tests

- **Unit**: `UserManagementServiceTest`, `RoleQueryServiceTest`, `AuthenticationServiceTest`,
  `IdentityExceptionToApiExceptionMapperTest`, `UserSessionsTest`.
- **Repository**: `JpaIdentityRepositoryTest` and `IdentityPersistenceConventionsTest` (PostgreSQL,
  Testcontainers).
- **Integration**: `UsersApiIntegrationTest`; `EndpointPermissionsIntegrationTest` covers the new
  endpoints' permissions; `LoginLockoutIntegrationTest`, `PasswordChangeIntegrationTest` and
  `AuthApiIntegrationTest` still pass after the `UserSessions` refactor.
- **The issue's acceptance criteria**:
  - "Tests cover every endpoint, every guard rail and the permission checks" is
    `UsersApiIntegrationTest`, with the guard rails also in `UserManagementServiceTest`.
  - "A disabled user's session stops working immediately" is the disable case of
    `UsersApiIntegrationTest`: the next request is 401, and the stream is closed.
- **No frontend, e2e or screenshot change**: the UI is #89. `mise run check` (`vue-tsc`) proves that
  the regenerated `schema.d.ts` still fits the frontend.
- **The developer agent cannot run containers**, so the Testcontainers tests run in CI. The
  session that drives this plan runs them locally as well (`DOCKER_HOST` set to the Podman socket).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Development → Login" | Replace the unlock bullet with a user-management bullet: every endpoint with its body and answers, the generated one-time password shown once, the flag, the guard rails and their 409 codes, session ending on disable, reset and role change, no hard delete, `lastLoginAt`, `GET /api/roles`. Add the new endpoints to the permission table (`GET /api/users…`, `GET /api/roles`: `user:read`; `GET /api/roles` also `role:manage`; `POST`/`PATCH /api/users…`: `user:manage`). Extend the sentence about streams closing when a session ends with "a disable or password reset by an admin" |
| Text | `docs/coding-convention/backend-java-persistence.md` | Timestamps table: add `USERS.LAST_LOGIN_AT` as a domain fact (set by the identity core from the `Clock` on a successful login). Optimistic locking: identity now has `CONCURRENT_UPDATE` (`IdentityError`), so the sentence "a domain … no service writes yet (identity)" changes |
| Text | `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | Regenerated with `mise run api-types` |
| Screenshot | none | No page changes; the admin pages and their screenshots come with #89 |

## Out of scope

- The admin UI (Admin → Users) and its e2e test: #89.
- Creating, changing and deleting roles (`POST`/`PATCH`/`DELETE /api/roles`), and permission
  changes that take effect without a re-login: #90.
- Audit rows for these actions: #93. This plan only logs them.
- Renaming a user and deleting a user (the issue rules out hard delete).
- Running more than one backend instance: the guard-rail lock, like the sessions, assumes one.
