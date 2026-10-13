# Plan: Login rate limit and account lockout

- **Issue**: #85 (Auth: login rate limit and account lockout)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): a new
  feature; the API only gains an endpoint and a new answer (429) on the login

## Goal

Password guessing is slowed down. After 5 wrong passwords in a row (configurable), an account is
locked for 15 minutes (configurable). While it is locked, every login fails with the same 401
`bad_credentials` as a wrong password, even with the right password, so an attacker cannot tell a
locked account from a wrong guess or an unknown user. A successful login resets the count. Each
client IP address gets a limited number of failed logins in a sliding window (20 in 5 minutes,
configurable). After that the address gets 429 `too_many_login_attempts` with `Retry-After` until
the window frees up, so one host cannot try many usernames. An admin (`user:manage`) can unlock an
account early with `POST /api/users/{id}/unlock`. The login page shows a clear message for the 429.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend, identity core | `backend-java-package-structure.md`: use cases in `domain.identity.core.inbound`, package-private `*Service` in `…service`, error codes in `IdentityError` next to `IdentityException`, no Spring Security or persistence types in the core. `backend-java-persistence.md`: `LOCKED_UNTIL` is a **domain fact** (the core sets it from the injected `java.time.Clock` and decides with it), so no auditing; writes go through `UserRepositoryPort#save` with the optimistic lock (`User.version`) |
| backend, identity adapter | `backend-java-persistence.md`, `backend-database-naming.md`: no new migration is needed. `FAILED_LOGIN_COUNT` and `LOCKED_UNTIL` exist since `V1__create_users_and_roles.sql`, and `UserEntity`, the mappers and `JpaUserRepositoryAdapter#save` already carry them. Only the round trip test is added |
| backend, BFF | `backend-java-package-structure.md` and the "BFF api holds no logic" rule (`bff_api_holds_no_security_code`): the new `UsersApiController`, `UsersApiDelegate` and DTO-free endpoint in `bff.controller.api`; `UsersApiDelegateImpl` in `bff.delegate.impl`; the IP limit filter, its limiter and the `user:manage` route rule in `bff.delegate.impl.config`; the new error codes mapped in `IdentityExceptionToApiExceptionMapper` |
| backend, application | `application.properties`: new `platform.login.*` properties with environment overrides, as `platform.password.min-length` does |
| frontend | `frontend-folder-structure.md`: only the i18n catalogues (`shared/i18n/locales/en.json`, `tr.json`) get the new error code; `LoginForm.vue` already shows any error code through `useLabels().error` |
| docs | `README.md` "Login" section; Checkstyle (`backend-java-checkstyle.md`) and Spotless for all Java |

## Design

### Account lockout (identity core)

`AuthenticationService#authenticate` (today: unknown user → decoy hash check → `BAD_CREDENTIALS`;
wrong password or disabled → `BAD_CREDENTIALS`; match → optional rehash) becomes:

1. Unknown user: unchanged (decoy hash check, `BAD_CREDENTIALS`). Nothing is counted: there is no
   row. The IP limit covers spraying unknown names.
2. Known user, **locked** (`lockedUntil` present and after `clock.instant()`): check the password
   anyway (same timing as a wrong one), then throw `BAD_CREDENTIALS`. Nothing is counted and the lock
   is not extended, so a guesser cannot keep an account locked by hammering it during the lock.
3. Wrong password: `failedLoginCount + 1`. When it reaches the threshold, set
   `lockedUntil = now + lockout duration` and set the count back to 0, so after the lock expires the
   user gets the full number of attempts again. Save, then throw `BAD_CREDENTIALS`. The log line at
   INFO says "User <id> locked until <instant> after <n> failed logins" (the ID, never the username
   or password).
4. Right password, not disabled, not locked (or the lock has expired): when `failedLoginCount > 0`
   or `lockedUntil` is present, clear both. The rehash, if needed, goes into the **same** save, so a
   login writes at most once and a normal login (count 0, no lock, no rehash) writes nothing.
5. Disabled user with the right password: `BAD_CREDENTIALS` as today, not counted.

The `AuthenticateUseCase#authenticate` Javadoc gets the lockout: "a locked account fails like a wrong
password".

**Concurrent failed logins**: two wrong passwords at once both read version v; the second save fails
with `OptimisticLockingFailureException`. The service reloads the user and applies the step again,
up to 3 times. If it still conflicts, it logs at WARN and answers `BAD_CREDENTIALS` without
counting. The answer never changes because of a conflict. The success-path reset uses the same
retry. The rehash conflict handling (log and go on) stays.

`User` gets two `with…` methods in the style of `withPassword`:
`withFailedLogin(int count, Optional<Instant> lockedUntil)` and `withLoginSucceeded()` (count 0, no
lock), plus `isLockedAt(Instant now)`. These are value transitions on the record. The decision when
to lock stays in the service.

**Settings**: `LoginLockoutPolicy`, a package-private `@Component` in `…core.service`, built like
`PasswordPolicy`:
- `@Value("${platform.login.max-failed-attempts:5}") int maxFailedAttempts`, 1 to 100;
- `@Value("${platform.login.lockout-duration:15m}") Duration lockoutDuration`, 1 minute to 24 hours.

Values outside these ranges make startup fail with an `IllegalArgumentException` naming the
property, as `PasswordPolicy` does. `AuthenticationService` gets `Clock` (the application's one bean)
and `LoginLockoutPolicy` injected.

**Unlock**: a new `UnlockUserUseCase` (`…core.inbound`) with `void unlock(UserId id)`, implemented by
a new package-private `UserLockService` (`…core.service`). It loads the user, and when the user is
locked or has failed logins it saves `withLoginSucceeded()`, with the same retry on
`OptimisticLockingFailureException`. Unlocking a user that is not locked succeeds and writes
nothing (idempotent). An unknown ID throws the new `IdentityError.USER_NOT_FOUND`
(params `{id}`). It logs "User <id> unlocked" at INFO. #93 (audit log) will record who did it; the
actor is not passed in yet.

`IdentityError` gains `USER_NOT_FOUND`. `IdentityExceptionToApiExceptionMapper` maps it to 404
(`user_not_found`).

### IP rate limit (BFF)

It works per request, not per account, so it lives in the BFF's security configuration
(`bff.delegate.impl.config`), not in the identity core:

- `LoginAttemptLimiter` (package-private): an in-memory sliding window of **failed** login times
  per client address (`ConcurrentHashMap<String, Deque<Instant>>`, guarded per entry). It takes the
  `Clock` and has two methods: `Optional<Duration> blockedFor(String address)` (empty while under
  the limit; otherwise the time until the oldest failure leaves the window) and
  `recordFailure(String address)`. Only failures count, so a busy but honest host (many logins
  behind one address) is not slowed down. Old entries are dropped on access. Memory is bounded: at
  most 10,000 tracked addresses; when full, addresses whose window is empty are removed first, then
  the oldest. Settings:
  `platform.login.ip-max-failed-attempts` (default 20, 1 to 10,000) and `platform.login.ip-window`
  (default `5m`, 1 minute to 24 hours), checked at startup like the lockout settings.
- `LoginRateLimitFilter` (`OncePerRequestFilter`, package-private) on `POST /api/auth/login` only:
  - If the address is blocked, it answers 429 `too_many_login_attempts` through `ApiErrorWriter`
    with `Retry-After: <seconds, rounded up>`, and the login never runs.
  - Otherwise it runs the chain and, when the response is 401, records a failure. That covers wrong
    passwords, unknown users and locked accounts alike.
  - It is added in `SecurityConfig` before `CsrfFilter`, so a blocked host is turned away before any
    other work.
- **Client address** = `HttpServletRequest#getRemoteAddr()`. `X-Forwarded-For` is not trusted: any
  client can set it, and the platform has no reverse proxy in front of it (it is published on
  127.0.0.1, reached over the tailnet, #76). Under Docker Compose the published port can hide the
  client address behind the bridge gateway. Then all clients share one address and the limit
  applies to them together, which is acceptable for a small trusted user base and is written in the
  README.
- **Why not Bucket4j**: a token bucket limits all requests, but the limit here is on failures, and a
  sliding window over a deque is a few dozen lines. That avoids a new dependency to declare, keep
  updated and approve under `repository-dependency-hygiene.md`.
- The counts live in memory, like the sessions: a restart clears them.

`ApiErrorWriter` already writes the `ApiErrorDto` body; the filter sets `Retry-After` itself before
calling it.

### Unlock endpoint (BFF)

- `UsersApiController` (`bff.controller.api`, `@RequestMapping("/api/users")`):
  `@PostMapping("/{id}/unlock")` → `ResponseEntity<Void>`, delegating to `UsersApiDelegate#unlock(String id)`.
- `UsersApiDelegateImpl` (`bff.delegate.impl`): calls `UnlockUserUseCase` with `new UserId(id)` and
  answers 204. `IdentityException` goes through `IdentityExceptionToApiExceptionMapper`
  (`user_not_found` → 404).
- `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/users/*/unlock").hasAuthority(Permission.USER_MANAGE.key())`
  before the `/api/**` → `authenticated()` rule. A user without it gets 403 `forbidden`
  (`ApiAccessDeniedHandler`). The rule lives in `SecurityConfig` and not as `@PreAuthorize` on the
  controller, since `bff.controller.api` must not depend on Spring Security
  (`bff_api_holds_no_security_code`). #87 decides how every endpoint is checked. #88 adds the other
  `/api/users` endpoints next to this one, and #89 the button.
- The `PasswordChangeRequiredFilter` keeps applying: a session that must change its password gets 403
  `password_change_required` here too.
- `artifact/frontend/openapi.json` and the frontend's generated API types are refreshed
  (`mise run api-types`).

### Configuration (`application.properties`, "Login" block)

```properties
# Lockout (#85): after this many wrong passwords in a row an account is locked for the duration.
platform.login.max-failed-attempts=${PLATFORM_LOGIN_MAX_FAILED_ATTEMPTS:5}
platform.login.lockout-duration=${PLATFORM_LOGIN_LOCKOUT_DURATION:15m}
# Per client address: this many failed logins within the window, then 429 until the window frees up.
platform.login.ip-max-failed-attempts=${PLATFORM_LOGIN_IP_MAX_FAILED_ATTEMPTS:20}
platform.login.ip-window=${PLATFORM_LOGIN_IP_WINDOW:5m}
```

### Frontend

`en.json` / `tr.json` `errors`: `"too_many_login_attempts": "Too many failed logins from this
address. Try again in a few minutes."` (and the Turkish translation in `tr.json`) and
`"user_not_found"`. `LoginForm.vue` already shows the message of any error code; no component
changes.

## Work packages

### WP1: Lockout and unlock in the identity core

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/…/domain/identity/core/model/User.java`
  - `…/core/exception/IdentityError.java`
  - `…/core/inbound/AuthenticateUseCase.java` (Javadoc), `…/core/inbound/UnlockUserUseCase.java` (new)
  - `…/core/service/AuthenticationService.java`, `…/core/service/LoginLockoutPolicy.java` (new),
    `…/core/service/UserLockService.java` (new)
  - tests: `…/core/model/UserTest.java`, `…/core/service/AuthenticationServiceTest.java`,
    `…/core/service/LoginLockoutPolicyTest.java` (new), `…/core/service/UserLockServiceTest.java` (new)
  - `artifact/backend/domain/identity/adapter/src/test/java/…/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [x] `User`: `withFailedLogin`, `withLoginSucceeded`, `isLockedAt`
  - [x] `LoginLockoutPolicy` with its two settings and range checks
  - [x] `AuthenticationService`: the five cases above, `Clock` and policy injected, one save per
        login at most, the retry on optimistic-lock conflicts
  - [x] `IdentityError.USER_NOT_FOUND`, `UnlockUserUseCase`, `UserLockService`
- **Tests**:
  - `AuthenticationServiceTest` (fixed / advancing clock, in-memory or mocked port as the test does today):
    - the count rises with each wrong password;
    - the 5th wrong password locks for 15 minutes and resets the count;
    - the 6th attempt with the **right** password fails with `BAD_CREDENTIALS`, the same message and
      no params, and is not counted;
    - after the lock expires the right password succeeds and clears count and lock;
    - a success after 3 failures resets the count;
    - a normal login writes nothing;
    - rehash and reset go into one save;
    - an unknown user is not counted;
    - a disabled user with the right password is not counted;
    - an optimistic-lock conflict is retried, and after 3 conflicts the answer is still `BAD_CREDENTIALS`.
  - `LoginLockoutPolicyTest`: defaults, and the bounds of both settings.
  - `UserLockServiceTest`: unlock clears the lock and count, an unlocked user is not written,
    an unknown ID gives `USER_NOT_FOUND`, a conflict is retried.
  - `UserTest`: the new transitions keep every other field.
  - `JpaIdentityRepositoryTest`: `failedLoginCount` and `lockedUntil` round-trip through a save.

### WP2: IP limit, unlock endpoint and configuration in the BFF

- **Depends on**: WP1 (`UnlockUserUseCase`, `USER_NOT_FOUND`)
- **Files**:
  - `artifact/backend/bff/api/src/main/java/…/bff/controller/api/UsersApiController.java` (new),
    `UsersApiDelegate.java` (new)
  - `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/UsersApiDelegateImpl.java` (new)
  - `…/bff/delegate/impl/config/LoginAttemptLimiter.java` (new), `LoginRateLimitFilter.java` (new),
    `SecurityConfig.java`
  - `…/bff/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapper.java`
  - `artifact/backend/src/main/resources/application.properties`
  - tests: `artifact/backend/bff/impl/src/test/java/…/config/LoginAttemptLimiterTest.java` (new),
    `LoginRateLimitFilterTest.java` (new), the mapper's test if one exists;
    `artifact/backend/src/test/java/…/LoginLockoutIntegrationTest.java` (new),
    `artifact/backend/src/test/java/…/AuthApiIntegrationTest.java` (only if a shared helper is needed)
- **Steps**:
  - [ ] `LoginAttemptLimiter` with the `Clock`, its settings, range checks and the memory bound
  - [ ] `LoginRateLimitFilter` (429, `Retry-After`, counts only 401 answers of `POST /api/auth/login`),
        registered before `CsrfFilter`
  - [ ] the `/api/users/*/unlock` rule with `user:manage`
  - [ ] `UsersApiController`, `UsersApiDelegate`, `UsersApiDelegateImpl`
  - [ ] `user_not_found` → 404 in the mapper
  - [ ] the four `platform.login.*` properties with comments
- **Tests**:
  - `LoginAttemptLimiterTest` (mutable clock):
    - under the limit nothing is blocked, and the 20th failure blocks;
    - `blockedFor` is the time until the oldest failure leaves the window, and the address is free
      again after it;
    - two addresses are counted apart;
    - at the memory bound, expired addresses go first.
  - `LoginRateLimitFilterTest` (mock request and chain):
    - a blocked address gets 429 with the error body and `Retry-After`, and the chain is not called;
    - a 401 answer is recorded, a 200 or 403 answer is not;
    - other paths and methods pass untouched.
  - `LoginLockoutIntegrationTest` (`@SpringBootTest` on Testcontainers PostgreSQL, like
    `PasswordChangeIntegrationTest`, with `platform.login.*` set low where it helps, and a test clock
    or a short lockout):
    - **the issue's acceptance criterion**: N wrong logins, then the right password gets 401
      `bad_credentials` with the same body as a wrong password;
    - after the lock expires it gets 200;
    - after `POST /api/users/{id}/unlock` by the admin (204) it gets 200;
    - unlock without a session is 401, and unlock of an unknown ID is 404 `user_not_found`;
    - from one address, failed logins beyond the IP limit get 429 with `Retry-After`, even for an
      account that is not locked.
    - **403 without `user:manage`**: today only the first admin exists, and there is no API to create
      a user (#88). The test inserts a second user without roles through `UserRepositoryPort`, as the
      other integration tests seed data, and expects 403 `forbidden`.
  - `ArchitectureTest` passes unchanged: no Spring Security in `bff.controller.api`.

### WP3: Frontend messages, API types and docs

- **Depends on**: WP2 (the endpoint for `openapi.json`, the error codes)
- **Files**:
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
  - `artifact/frontend/openapi.json` and the generated API types (`mise run api-types`)
  - `README.md`
  - `artifact/frontend/src/features/auth/` tests (`LoginForm` spec if one exists, otherwise a new
    `LoginForm.spec.ts`)
- **Steps**:
  - [ ] the two error messages in both catalogues
  - [ ] refresh `openapi.json` and the API types
  - [ ] the README changes below
- **Tests**:
  - A frontend unit test: the login form shows "Too many failed logins…" for a 429
    `too_many_login_attempts` answer.
  - Any existing i18n key-parity test keeps both catalogues in step.

## Tests

- **Unit**: lockout counting, locking, expiry, reset and retry in `AuthenticationServiceTest`; the
  settings in `LoginLockoutPolicyTest`; unlock in `UserLockServiceTest`; the sliding window in
  `LoginAttemptLimiterTest`; the filter in `LoginRateLimitFilterTest`; the login form's message.
  Together they cover the issue's "tests cover the counter, the expiry and the IP limit".
- **Adapter**: `JpaIdentityRepositoryTest`, the round trip of the two columns.
- **Integration**: `LoginLockoutIntegrationTest` proves the issue's acceptance criterion end to end
  through HTTP: the (N+1)th attempt is rejected even with the right password, and it succeeds after
  the lock expires or an admin unlocks.
- **End-to-end**: no new e2e test. The e2e suite runs every test from 127.0.0.1 as the one shared
  admin (`e2e/tests/support.ts`), and there is no way to create a second user before #88. Locking
  that admin, or exhausting the address's failure budget, would break the parallel tests. The
  existing e2e tests stay below both limits: two failed logins in `auth.e2e.ts`, five or fewer with
  retries. Leave a comment in `auth.e2e.ts` that says so. #89's e2e test can cover the unlock
  button with its own user.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Login", the backend list | A paragraph on lockout and the IP limit: 5 wrong passwords in a row lock an account for 15 minutes; a locked account answers the same 401 `bad_credentials`; a success resets the count; 20 failed logins per client address in 5 minutes, then 429 `too_many_login_attempts` with `Retry-After`; the counts live in memory like the sessions; under Docker Compose all clients may share one address. The four `PLATFORM_LOGIN_*` variables with their defaults and ranges. `POST /api/users/{id}/unlock` (`user:manage`): 204, 404 `user_not_found`, 403 `forbidden` |
| Text | `README.md`, "Login", the `POST /api/auth/login` bullet | Add "or 429 `too_many_login_attempts`" |
| Text | `README.md`, the CI table's end-to-end row | No change (no new e2e test) |
| API | `artifact/frontend/openapi.json`, generated frontend types | The unlock endpoint (`mise run api-types`) |
| Screenshot | none | The login page looks the same. Only an error message is new, and the README does not show the page in an error state |

## Out of scope

- The rest of the user management API (`GET/POST/PATCH /api/users…`): #88. The unlock button and the
  "locked" status in the users table: #89.
- Per-endpoint permission checks across the API (`@PreAuthorize` or otherwise): #87. Here only the
  unlock route is checked, in `SecurityConfig`.
- Recording who unlocked whom, and lockouts, in the audit log: #93.
- Counting wrong *current* passwords on `POST /api/auth/password`: it needs a valid session, so it
  is not an open guessing surface.
- Trusting `X-Forwarded-For` or a configured reverse proxy: there is no proxy yet. It comes with
  exposing the platform beyond the tailnet (#76).
- A shared, persistent rate limit store (several backend instances): the platform runs as one
  instance.
