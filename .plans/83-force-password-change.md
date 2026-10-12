# Plan: Force a password change after a one-time password

- **Issue**: #83 (Auth: force a password change after a one-time password)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): a new
  endpoint and a new 403 reason; nothing existing changes shape

## Goal

A user whose account carries `mustChangePassword` (today: the first admin with a generated password,
#82; later: new users and admin resets, #88) can do nothing in the app until they set a new password.
The UI takes them to a change-password page right after login, and every other API call answers 403
`password_change_required`. Any signed-in user can change their own password later from the user
menu. After a change, the flag is cleared and the user's other sessions are logged out.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend, identity domain (`:backend:domain:identity:core`) | `backend-java-package-structure.md`: the use case in `core.inbound`, its service package-private in `core.service`, new error codes in `core.exception.IdentityError`. No adapter or persistence change: `User`, its `mustChangePassword` column and `UserRepositoryPort.save` exist (#79) |
| Backend, BFF (`:backend:bff:api`, `:backend:bff:impl`) | `backend-java-package-structure.md` and the memory rule "BFF api holds no logic": the endpoint and request DTO in `bff.controller.api` / `.model`; the delegate implementation in `bff.delegate.impl`; the new filter and the session registry in `bff.delegate.impl.config` (`bff_api_holds_no_security_code`); error mapping in `bff.delegate.impl.mapper.error`. `backend-java-checkstyle.md`, `backend-java-formatting.md` |
| Frontend | `frontend-folder-structure.md`: the backend call in `features/auth/api.ts`, the form in `features/auth/components/`, the route component in `pages/`, the route in `app/router/index.ts`, the 403 hook in `shared/api/client.ts` (generic, knows no feature) wired up in `app/main.ts`, translations in `shared/i18n/locales/{en,tr}.json`. Prettier, ESLint boundaries |
| API types | `artifact/frontend/openapi.json` and `src/shared/api/schema.d.ts` regenerated with `mise run api-types`; `types.ts` gets the request type |
| e2e, screenshots | the existing `e2e/tests/*.e2e.ts` and `e2e/screenshots/*.shot.ts` patterns; tests run in parallel against one platform with one shared admin session |
| Docs | `README.md` ("3. Log in", "Login"), `docs/screenshots/` |

## Design

### Identity domain

- `IdentityError` gets `WRONG_CURRENT_PASSWORD` and `PASSWORD_UNCHANGED`.
- New inbound port `ChangePasswordUseCase` (`core.inbound`):
  `AuthenticatedUser changePassword(UserId id, CharSequence currentPassword, CharSequence newPassword)`.
  Javadoc: the current password must match (`WRONG_CURRENT_PASSWORD` otherwise), the new one must
  differ from it (`PASSWORD_UNCHANGED`), and the stored user gets the new hash and
  `mustChangePassword = false`. An unknown or disabled user fails with `BAD_CREDENTIALS` (the BFF ends
  the session then, as `me` does). It says, like `CreateFirstAdminUseCase`, that the password policy
  check (#84) belongs in the implementation.
- New `PasswordChangeService` (`core.service`, package-private, `@Service`), with
  `UserRepositoryPort` and `PasswordHasherPort`, builds the updated `User` from the stored one
  (version carried along, so a concurrent change fails with `OptimisticLockingFailureException`;
  let it propagate, it is a 409/500 corner case and not worth a code path here). To return the
  `AuthenticatedUser`, it needs the role names and permissions: move the `toAuthenticated` logic of
  `AuthenticationService` into a package-private helper in `core.service` (e.g.
  `AuthenticatedUserFactory`, with `RoleRepositoryPort`) used by both services, instead of copying it.
- Add a `User.withPassword(PasswordHash hash, boolean mustChangePassword)` wither next to
  `withRoleIds`, and use it in `AuthenticationService.rehash` too.
- Never log the passwords; the service logs at INFO "Password changed for user <id>".

### BFF

- `POST /api/auth/password` on `AuthApiController` (consumes and produces JSON) with
  `ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword)` in
  `bff.controller.api.model`, delegating to a new `AuthApiDelegate.changePassword(request, http,
  response)`. Answers 200 with the `CurrentUserDto` (now `mustChangePassword: false`).
- `AuthApiDelegateImpl.changePassword`:
  1. no authenticated user → 401 `unauthorized` (the URL needs a session anyway);
  2. calls `ChangePasswordUseCase`; `BAD_CREDENTIALS` (user gone or disabled) → end the session and
     401, like `me`; the other errors go through the error mapper;
  3. expires the user's other sessions (below);
  4. replaces the authentication in the security context with a fresh one built from the returned
     user (so the marker authority below is gone), applies the `SessionAuthenticationStrategy`
     (new session ID and CSRF token, as at login, reading the token so the response sets it), and
     saves the context.
- `IdentityExceptionToApiExceptionMapper`: `WRONG_CURRENT_PASSWORD` and `PASSWORD_UNCHANGED` → 400
  with `wrong_current_password` / `password_unchanged`. Not 401: the UI treats any 401 but login's
  as an ended session.
- **The gate.** At login (and after a change) the authentication gets an extra authority,
  `PASSWORD_CHANGE_REQUIRED`, when `mustChangePassword` is set (a constant shared by the delegate and
  the filter, in `bff.delegate.impl.config`). A new `PasswordChangeRequiredFilter`
  (`bff.delegate.impl.config`, a `OncePerRequestFilter`, added after `AuthorizationFilter`, so 401s
  and CSRF failures come first) answers 403 `password_change_required` through `ApiErrorWriter` for
  every `/api/**` request of such a session except `GET /api/auth/me`, `POST /api/auth/password` and
  `POST /api/auth/logout`. Open routes (`/actuator/**`, `/api/openapi/**`, the static UI) stay open.
  The flag lives in the session, not read from the database per request: the only ways to set it on
  an existing user (an admin reset, #88) must log that user's sessions out anyway, which #88 does
  with the session registry below. Say so in the filter's Javadoc.
- **Other sessions.** Sessions live in the server's memory (Tomcat). `SecurityConfig` gets a
  `SessionRegistry` bean (`SessionRegistryImpl`), an `HttpSessionEventPublisher` bean (so ended
  sessions leave the registry), a `RegisterSessionAuthenticationStrategy` in the composite
  `SessionAuthenticationStrategy` (so the custom login registers its session under the principal,
  the user ID string), and `sessionManagement(...).maximumSessions(-1).sessionRegistry(...)` with an
  `expiredSessionStrategy` that writes 401 `unauthorized` via `ApiErrorWriter` (the UI then goes to
  the login page). On a password change, `sessionRegistry.getAllSessions(userId, false)` minus the
  current session ID → `expireNow()` on each. The current session keeps going under its new ID: make
  sure the registry follows the session ID change (`SessionRegistryImpl` listens to
  `SessionIdChangedEvent`, published through `HttpSessionEventPublisher`); a test proves it.

### Frontend

- `features/auth/api.ts`: `authApi.changePassword(currentPassword, newPassword)` →
  `POST /api/auth/password`, returns `CurrentUser`. `features/auth/store.ts`:
  `changePassword(...)` stores the returned user; a getter `mustChangePassword`.
- `shared/api/client.ts`: like `onUnauthorized`, a generic `onForbiddenReason(errorCode, handler)`
  hook (or a single `onPasswordChangeRequired`, if that reads simpler: shared knows the error code
  only, no feature) called on a 403 with that `errorCode`, before the `ApiError` is thrown.
  `features/auth/index.ts` re-exports it as `onPasswordChangeRequired`; `app/main.ts` wires it to
  `router.replace({ name: 'change-password', query: { redirect } })`.
- `app/router/index.ts`: route `/password` (`name: 'change-password'`, page
  `pages/ChangePasswordPage.vue`). The `beforeEach` guard, after the session is loaded, sends a user
  with `mustChangePassword` to `change-password` (keeping the target in `redirect`) for any route but
  `login` and `change-password`. While forced, the page is shown bare (`meta.bare` is static, so the
  page uses its own condition or the shell reads `session.mustChangePassword`): no navigation that
  only bounces back, but the logout action stays reachable (the user menu or a logout link on the
  page).
- `features/auth/components/ChangePasswordForm.vue` (exported from `index.ts`): current password,
  new password, repeat new password (Naive UI form, like `LoginForm.vue`); the repeat must match
  (checked in the form, never sent); shows `wrong_current_password` and `password_unchanged` next
  to the field, other errors through `useLabels().error`. Emits `changed`.
- `pages/ChangePasswordPage.vue`: a card titled "Change password"; when forced, a short notice
  ("Your password is a one-time password. Set a new one to continue."). On `changed`: a success
  message, then `safeRedirect(route.query.redirect)`.
- `UserMenu.vue`: a "Change password" entry before "Log out" (replaces the comment that says it
  comes later), routing to `change-password`.
- `LoginForm`/`LoginPage`: after a login whose user must change the password, go to
  `change-password` with the same `redirect`.
- i18n keys in `en.json` and `tr.json` (`password.*`, `errors.wrong_current_password`,
  `errors.password_unchanged`, `errors.password_change_required`, `nav.changePassword`).

## Work packages

### WP1: Identity domain: change the password

- **Depends on**: none
- **Files**: `domain/identity/core/.../exception/IdentityError.java`,
  `.../inbound/ChangePasswordUseCase.java` (new), `.../service/PasswordChangeService.java` (new),
  `.../service/AuthenticatedUserFactory.java` (new, extracted), `.../service/AuthenticationService.java`,
  `.../model/User.java`; tests `.../service/PasswordChangeServiceTest.java` (new),
  `AuthenticationServiceTest.java`, `UserTest.java`
- **Steps**:
  - [ ] error codes, use case with Javadoc, `User.withPassword`
  - [ ] extract the `AuthenticatedUser` building into a shared helper; `AuthenticationService` uses it and `withPassword`
  - [ ] `PasswordChangeService`
- **Tests**: `PasswordChangeServiceTest`: a correct change stores the new hash, clears the flag,
  keeps roles, version and the other fields, and returns the user with roles and permissions; a
  wrong current password → `WRONG_CURRENT_PASSWORD` and nothing saved; the same password →
  `PASSWORD_UNCHANGED`; unknown and disabled user → `BAD_CREDENTIALS`; the passwords never appear in
  a log line (as the first-admin tests check). `AuthenticationServiceTest` still green after the
  extraction. `UserTest` covers `withPassword`.

### WP2: BFF: endpoint, gate and session invalidation

- **Depends on**: WP1
- **Files**: `bff/api/.../AuthApiController.java`, `AuthApiDelegate.java`,
  `model/ChangePasswordRequest.java` (new); `bff/impl/.../AuthApiDelegateImpl.java`,
  `config/SecurityConfig.java`, `config/PasswordChangeRequiredFilter.java` (new),
  `config/ApiAccessDeniedHandler.java` (only if the shared error codes move there),
  `mapper/error/IdentityExceptionToApiExceptionMapper.java`; tests
  `IdentityExceptionToApiExceptionMapperTest.java`,
  `artifact/backend/src/test/.../AuthApiIntegrationTest.java` (or a new
  `PasswordChangeIntegrationTest.java` next to it), `TestSession.java` (a `createUser` variant with
  `mustChangePassword` and a `changePassword` helper); `artifact/frontend/openapi.json`,
  `src/shared/api/schema.d.ts` (`mise run api-types`)
- **Steps**:
  - [ ] endpoint, DTO, delegate method
  - [ ] the marker authority at login, the filter and its place in the chain
  - [ ] session registry, event publisher, registering strategy, expired-session strategy; expire the other sessions on a change
  - [ ] error mapping
  - [ ] regenerate the API types
- **Tests** (integration, over HTTP like `AuthApiIntegrationTest`):
  - a flagged user gets 403 `password_change_required` on `GET /api/analyses`, `GET /api/presets` and
    an unsafe call (`POST /api/presets`), while `me` (200, `mustChangePassword: true`), `logout` and
    `password` work; an unflagged user is not gated;
  - a wrong current password → 400 `wrong_current_password`, the flag stays and the old password
    still logs in; the same password → 400 `password_unchanged`; an empty field → 400
    `invalid_request`; without a session → 401; without the CSRF header → 403 `invalid_csrf_token`;
  - after a change: 200 with `mustChangePassword: false`, `GET /api/analyses` works in the same
    session, the session ID and CSRF token changed, `me` and the stored user show the flag cleared, the old password fails and the new one logs in;
  - two sessions of one user (two logins): after a change in the first, the second gets 401
    `unauthorized` on its next call, the first keeps working, and a third user's session is
    untouched;
  - a session that changed its ID at login still counts as "current" (the registry followed it).
  - `IdentityExceptionToApiExceptionMapperTest` covers the two new codes.

### WP3: Frontend: change-password page, forced redirect, user menu

- **Depends on**: WP2 (the generated types)
- **Files**: `artifact/frontend/src/shared/api/client.ts`, `client.spec.ts`,
  `src/shared/api/types.ts`, `src/features/auth/{api,store,index}.ts`, `store.spec.ts`,
  `src/features/auth/components/ChangePasswordForm.vue` (new),
  `src/features/auth/components/UserMenu.vue`, `LoginForm.vue` (or `pages/LoginPage.vue`),
  `src/pages/ChangePasswordPage.vue` (new), `src/app/router/index.ts`, `src/app/main.ts`,
  `src/app/App.vue` (only if the bare shell needs it), `src/shared/i18n/locales/{en,tr}.json`;
  a `features/auth/model/` helper with its spec if the guard's decision is worth testing on its own
  (e.g. `passwordGate.ts`: which route a user may see)
- **Steps**:
  - [ ] client hook, api and store
  - [ ] form, page, route, guard, login redirect, user menu entry
  - [ ] translations
- **Tests** (Vitest): `client.spec.ts`: a 403 `password_change_required` calls the hook, another
  403 does not, a 401 still calls only the session-ended handler; `store.spec.ts`:
  `changePassword` stores the returned user and clears `mustChangePassword`; the guard helper's
  spec: a flagged user is sent to `change-password` with the target in `redirect`, `login` and
  `change-password` are let through, an unflagged user is never redirected.

### WP4: e2e, screenshot and docs

- **Depends on**: WP3
- **Files**: `e2e/tests/password.e2e.ts` (new), `e2e/screenshots/password.shot.ts` (new),
  `docs/screenshots/change-password.png` (new), `README.md`
- **Steps**:
  - [ ] e2e tests
  - [ ] screenshot
  - [ ] README
- **Tests**: e2e, as the shared admin (whose password must not change: the tests run in parallel on
  that one session, and a change would log the others out): the user menu opens the change-password
  page; a wrong current password shows the field message and stays on the page; a mismatched repeat
  is caught in the form without a request. The successful change and the forced flow are proven by
  the backend integration tests and Vitest (a flagged user cannot be seeded in the e2e platform
  until the user management API, #88; its e2e tests can add the forced flow).

## Tests

- Unit: `PasswordChangeServiceTest`, `AuthenticationServiceTest`, `UserTest`,
  `IdentityExceptionToApiExceptionMapperTest`.
- Integration: the WP2 HTTP tests prove the three acceptance criteria: a flagged user reaches
  nothing but `me`, `password` and `logout` (403 `password_change_required`); a wrong current
  password is rejected (400 `wrong_current_password`); after the change the flag is cleared and the
  other sessions get 401.
- Frontend: Vitest for the client hook, the store and the guard.
- e2e: the page, the menu entry and the wrong-password message.
- `mise run check` and ArchUnit: the filter and registry stay in `bff.delegate.impl.config`, the
  service package-private in `core.service`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "3. Log in" | Replace "(the change-password screen comes with #83)" with what happens: after logging in with the generated password the UI asks for a new one before anything else |
| Text | `README.md`, "Login" | A paragraph on changing the password (user menu → Change password, page `/password`; forced while flagged; other sessions are logged out); the backend list gets `POST /api/auth/password` (`currentPassword`, `newPassword`: 200 with the user, 400 `wrong_current_password` / `password_unchanged`) and the rule that a flagged session gets 403 `password_change_required` on every `/api/**` route but `me`, `password` and `logout` |
| Text | `README.md`, CI table, "End-to-end tests" row | Add "change password (menu, wrong current password)" to the list of what the e2e tests cover |
| Screenshot | `docs/screenshots/change-password.png` (new), shown in README "Login" | The change-password page at `/password`, logged in as the e2e admin, opened from the user menu (not forced: a flagged user cannot be seeded yet), empty form, 1440×900 dark theme, by `e2e/screenshots/password.shot.ts` |
| API types | `artifact/frontend/openapi.json`, `schema.d.ts` | Regenerated with `mise run api-types` |

`login.png` does not change: the login page looks the same.

## Out of scope

- Password policy (length, common passwords, not the username): #84. The service has the one place
  to call it, said in the use case's Javadoc.
- Rate limit and lockout of wrong current passwords: #85.
- Setting the flag on new users and admin resets, and logging out a reset user's sessions: #88 (it
  reuses the session registry added here).
- The forced flow in the e2e tests: with #88/#89, once a flagged user can be created there.
- Audit log entries for a password change: #93.
