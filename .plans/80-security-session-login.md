# Plan: Spring Security baseline with session login, logout and CSRF

- **Issue**: #80 (Auth: Spring Security baseline with session login, logout and CSRF), part of #78
- **Plan**: 1 of 1 for this issue; depends on: none (#79, the identity domain, is merged)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): a new
  feature that is off by default, so nothing breaks for an existing installation

## Goal

The backend gets Spring Security with a server-side session login. `POST /api/auth/login` checks a
username and password against the identity domain's users, `GET /api/auth/me` returns the current
user with their roles and permissions, and `POST /api/auth/logout` ends the session. With login on,
every other `/api/**` route answers 401 without a session, and every unsafe request needs the CSRF
token that the SPA echoes from the `XSRF-TOKEN` cookie.

Login is behind a **temporary switch, `platform.auth.enabled` (`PLATFORM_AUTH_ENABLED`), off by
default**. A release with this change alone has nobody who can log in (the first admin comes with
#82) and no login page (#81). So, off, the platform behaves exactly as today. On, it has all of the
above, and the backend tests run with it on. Whichever of #81 and #82 is merged last turns login on
for good and removes the switch (see "Out of scope").

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: identity domain (`artifact/backend/domain/identity/core`) | `backend-java-package-structure.md`: the use case in `core.inbound`, its service package-private in `core.service`, the login result in `core.model`, the new error code in `core.exception` (`IdentityError`). `backend-java-persistence.md`: writes go through `UserRepositoryPort`, whose `save` may throw `OptimisticLockingFailureException`. The core has no Spring Security or web dependency (`domain_core_does_not_depend_on_infrastructure`) |
| Backend: BFF (`artifact/backend/bff/api`, `bff/impl`) | `backend-java-package-structure.md`: `AuthApiController` and `AuthApiDelegate` in the `bff.controller.api` root; DTOs (`*Dto`, `*Request`) in `…api.model`; web configuration in `…api.config` (next to `SpaWebConfig`); `AuthApiDelegateImpl` at the `bff.delegate.impl` root (nothing else may go there: `delegate_root_holds_only_delegate_impls`); `IdentityExceptionToApiExceptionMapper` in `…impl.mapper.error`. The BFF reaches the identity domain through its inbound port only (`bff_does_not_use_outbound_ports`), and `bff.api` uses no domain type (`bff_api_does_not_use_domain_models`) |
| Backend: application (`artifact/backend`) | `application.properties`: new `platform.auth.*` and `server.servlet.session.*` properties with a comment each, like the existing blocks. The starters go into `:backend` as `runtimeOnly`; the library modules declare libraries only (the comment in `artifact/backend/build.gradle.kts`) |
| Build | `repository-dependency-hygiene.md`: declare what you use. New entries in `gradle/libs.versions.toml` (versions from Spring Boot's BOM, as the other Spring entries), and `analyzeDependencies` must stay green |
| Frontend | API types only: `openapi.json` and `src/shared/api/schema.d.ts` are regenerated (`mise run api-types`), never edited by hand. No component changes: the login page and the CSRF header in `client.ts` are #81 |
| Docs | `README.md`, `docs/coding-convention/backend-java-package-structure.md` (see "Docs to update") |

## Design

### Identity core: authenticating a user

- `core/inbound/AuthenticateUseCase`:
  - `AuthenticatedUser authenticate(String username, CharSequence password)`: throws
    `IdentityException(BAD_CREDENTIALS)` for a username that is not a valid `Username`, an unknown
    user, a disabled user, or a wrong password, with the same message and no parameters each time, so
    a caller cannot tell which it was.
  - `Optional<AuthenticatedUser> findActiveUser(UserId id)`: the user as they are now; empty when they
    no longer exist or are disabled.
- `core/model/AuthenticatedUser(UserId id, Username username, Set<String> roleNames,
  Set<Permission> permissions, boolean mustChangePassword)`: the roles' names, and the union of their
  permissions, read through `RoleRepositoryPort.findById` for each of `User.roleIds()`. Today no role
  exists yet (#87 adds the built-in ones), so both sets may be empty; nothing in this plan checks a
  permission.
- `core/service/AuthenticationService` (package-private `@Service`, like `PresetService`), built on
  `UserRepositoryPort` and `PasswordHasherPort`, both from #79:
  - **Timing**: for an unknown or invalid username it still runs `PasswordHasherPort.matches` against
    a fixed hash made once at construction, so the response time does not reveal whether the user
    exists.
  - **Rehash**: when the password matches and `needsRehash` is true, it saves the user with a new hash
    from `hash`. If that save fails with `OptimisticLockingFailureException` (another login updated the
    row first), the login still succeeds and the error is only logged at debug.
  - It does not read `mustChangePassword`, `failedLoginCount` or `lockedUntil` beyond copying
    `mustChangePassword` into the result: forcing the change is #83, lockout is #85. Those rules go
    into this service later, which is why authentication is a use case of the identity core rather
    than a Spring `UserDetailsService` (see "Deviation from the issue" below).
- `core/exception/IdentityError`: add `BAD_CREDENTIALS`.
- `domain/identity/core/build.gradle.kts`: `implementation(libs.spring.context)` and
  `implementation(libs.spring.tx)` (for `@Service` and `OptimisticLockingFailureException`), as in
  `settings/core`.

### BFF: the auth endpoints

- `bff/controller/api/AuthApiController` (`/api/auth`), with `AuthApiDelegate`:
  - `POST /api/auth/login`, body `LoginRequest(@NotBlank String username, @NotBlank String password)`:
    200 with `CurrentUserDto`, 401 `bad_credentials` on wrong credentials.
  - `GET /api/auth/me`: 200 with `CurrentUserDto`, or 401 `unauthorized` without a session.
  - `POST /api/auth/logout`: invalidates the session, clears the security context, and expires the
    session and CSRF cookies; 204, also without a session. It is a controller method (Spring
    Security's own logout filter is turned off), so it is in the OpenAPI spec and the frontend gets
    its type.
- `bff/controller/api/model/CurrentUserDto(String id, String username, List<String> roles,
  List<String> permissions, boolean mustChangePassword)`: permissions as their keys
  (`analysis:read:all`, `Permission#key()`), sorted, so the response is stable.
- `bff/delegate/impl/AuthApiDelegateImpl` calls `AuthenticateUseCase` and does the Spring Security
  part of a login by hand, as Spring Security documents for a custom login endpoint:
  1. `authenticate` (an `IdentityException` goes through `IdentityExceptionToApiExceptionMapper`:
     `BAD_CREDENTIALS` → 401 `bad_credentials`, `INVALID_USERNAME` and `UNKNOWN_PERMISSION` → 500,
     since neither can reach it from a login);
  2. builds an authenticated `UsernamePasswordAuthenticationToken` whose principal is the user ID (a
     `String`, so the session holds no domain type), with the authorities `ROLE_<name>` for each role
     and each permission key;
  3. applies the filter chain's `SessionAuthenticationStrategy` (the session ID changes, and the CSRF
     token is renewed);
  4. saves the context through the chain's `SecurityContextRepository`
     (`HttpSessionSecurityContextRepository`).

  `me` reads the user ID from the session's context and calls `findActiveUser`. When that is empty (the
  user was disabled or deleted since the login), it ends the session and answers 401, so `/me` never
  returns a stale user. Ending the sessions of a disabled user on every other route is #88's.
- `bff/impl/build.gradle.kts`: `implementation(project(":backend:domain:identity:core"))` and the
  Spring Security libraries it uses.

### BFF: the security filter chain

`bff/controller/api/config/SecurityConfig` (`@Configuration`, `@EnableWebSecurity`) declares one
`SecurityFilterChain`, plus the `SecurityContextRepository`, `CsrfTokenRepository` and
`SessionAuthenticationStrategy` beans that both the chain and `AuthApiDelegateImpl` use, so a login
through the endpoint is stored and protected exactly as the chain expects. It reads
`platform.auth.enabled` with `@Value`, as the other `platform.*` properties are read.

**Switch off (the default)**: every request is permitted, CSRF is off, and no session is created for
an anonymous request: the platform behaves as today. The auth endpoints still answer, so #81 can be
developed against a backend with the switch on or off.

**Switch on**:

| Request | Rule |
|---|---|
| `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` | open (`/me` answers 401 itself) |
| `GET /api/openapi/**` | open: `mise run api-types` reads it from a running backend, and it holds no data |
| `/actuator/**` | open, as today: authentication for actuator/ops endpoints is out of scope of #78 |
| anything not under `/api/` | open: the static UI assets and the SPA's client routes (`SpaWebConfig`) |
| any other `/api/**` | needs a session; without one, 401 with an `ApiErrorDto` (`unauthorized`), never a redirect |

- **CSRF**: `csrf(csrf -> csrf.spa())` (Spring Security 7): `CookieCsrfTokenRepository` with the
  `XSRF-TOKEN` cookie readable by the SPA and the `X-XSRF-TOKEN` header, with BREACH protection. It
  applies to every unsafe method, `/api/auth/login` included (login CSRF). Every response, at the
  latest `GET /api/auth/me`, sets the cookie, so a client reads `/me` first and can then log in. A
  missing or wrong token gives 403 with an `ApiErrorDto` (`invalid_csrf_token`).
- **Session**: created at login only (`SessionCreationPolicy.IF_REQUIRED`, and nothing stores a
  context for anonymous requests). Session fixation protection changes the ID at login.
- **401 and 403 bodies**: an `AuthenticationEntryPoint` and an `AccessDeniedHandler` in `…api.config`
  write `ApiErrorDto` as JSON with the application's Jackson mapper, so the frontend's `ApiError`
  parses them like any other error.
- **No generated user**: Spring Boot's `UserDetailsServiceAutoConfiguration` would create an in-memory
  user and log its password, because there is no `UserDetailsService` or `AuthenticationProvider`
  bean. It is excluded (`spring.autoconfigure.exclude` in `application.properties`).
- **SSE**: `GET /api/analyses/{id}/events` goes through the same chain, so with the switch on it needs
  a session too. Closing open streams at logout is #86.

### Configuration (`application.properties`)

```properties
# --- Login (#78) ---------------------------------------------------------------------
# Temporary: off until the login page (#81) and the first admin (#82) exist; the later of the two
# turns it on for good and removes it.
platform.auth.enabled=${PLATFORM_AUTH_ENABLED:false}
# Sessions live in the server's memory: a restart logs everyone out. Idle timeout, configurable.
server.servlet.session.timeout=${PLATFORM_SESSION_TIMEOUT:30m}
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
```

`Secure` is not forced: Tomcat sets it on the cookie of a request that came over HTTPS. Behind a TLS
proxy that needs forwarded headers, which belongs with exposing the platform (#76), not here.

### Build

- `gradle/libs.versions.toml`: `spring-boot-starter-security`, `spring-security-core`,
  `spring-security-config`, `spring-security-web` and `spring-security-test` (versions from Boot's
  BOM). Check whether Boot 4 also needs `spring-boot-security` (the auto-configuration module) for
  the `exclude`, as it splits `spring-boot-data-jpa` and others out.
- `:backend`: `runtimeOnly(libs.spring.boot.starter.security)`; `testImplementation` of
  `spring-security-test` and `:backend:domain:identity:core` (for the test helper below).
- `:backend:bff:api`: `spring-security-config`, `-web`, `-core`, and the servlet API if it is not on
  its compile classpath already.

### Deviation from the issue

The issue asks for a `UserDetailsService` backed by the identity domain. The plan uses
`AuthenticateUseCase` in the identity core instead, called from the login endpoint. A
`UserDetailsService` would move the password check, and with it #83's forced change and #85's lockout
counter, into Spring Security's `DaoAuthenticationProvider`, outside the domain. It would also need
an adapter class in the BFF, where the `bff.delegate.impl` root may only hold delegate
implementations. The effect is the same: users come from the identity domain, and disabled users
cannot log in.

## Work packages

### WP1: Identity core: authenticate a user

- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/build.gradle.kts`
  - `…/domain/identity/core/inbound/AuthenticateUseCase.java` (new)
  - `…/domain/identity/core/model/AuthenticatedUser.java` (new)
  - `…/domain/identity/core/service/AuthenticationService.java` (new)
  - `…/domain/identity/core/exception/IdentityError.java`
  - `…/domain/identity/core/src/test/…/service/AuthenticationServiceTest.java` (new)
- **Steps**:
  - [ ] The use case, model, service and error code as in "Identity core" above
  - [ ] Javadoc on the use case: what each failure gives, and that it is the same for all of them
- **Tests**: `AuthenticationServiceTest`, with in-memory fakes of the two ports (as `PresetServiceTest`
  does for its ports):
  - a valid login returns the user, their role names and the union of the roles' permissions;
  - wrong password, unknown user, invalid username and disabled user each throw `BAD_CREDENTIALS`
    with the same message;
  - an unknown user still runs one `matches` call (the timing guard);
  - `needsRehash` saves a new hash once; an `OptimisticLockingFailureException` from that save still
    logs the user in;
  - `findActiveUser` is empty for a missing or disabled user.

### WP2: BFF: auth endpoints and the security filter chain

- **Depends on**: WP1
- **Files**:
  - `gradle/libs.versions.toml`, `artifact/backend/build.gradle.kts`,
    `artifact/backend/bff/api/build.gradle.kts`, `artifact/backend/bff/impl/build.gradle.kts`
  - `…/bff/controller/api/AuthApiController.java`, `AuthApiDelegate.java` (new)
  - `…/bff/controller/api/model/LoginRequest.java`, `CurrentUserDto.java` (new)
  - `…/bff/controller/api/config/SecurityConfig.java` and the entry point and access-denied handler
    classes (new)
  - `…/bff/delegate/impl/AuthApiDelegateImpl.java` (new)
  - `…/bff/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapper.java` (new) and its test
  - `artifact/backend/src/main/resources/application.properties`
- **Steps**:
  - [ ] Dependencies as in "Build"
  - [ ] The controller, delegate, DTOs and delegate implementation
  - [ ] `SecurityConfig` with both modes of the switch, the 401/403 JSON handlers, the shared beans
  - [ ] Exclude `UserDetailsServiceAutoConfiguration`; the properties block
- **Tests**: `IdentityExceptionToApiExceptionMapperTest` (next to the other two mapper tests): each
  error code's status and `errorCode`. The HTTP behaviour is tested in WP3.

### WP3: Backend integration tests with login on

- **Depends on**: WP2
- **Files**:
  - `artifact/backend/src/test/java/…/TestSession.java` (new)
  - `artifact/backend/src/test/java/…/AuthApiIntegrationTest.java` (new)
  - `AnalysesApiIntegrationTest.java`, `PresetsApiIntegrationTest.java`,
    `TradingPlatformApplicationTests.java`
- **Steps**:
  - [ ] `TestSession`: creates a user through the `UserRepositoryPort` and `PasswordHasherPort` beans
    (a unique username per call), reads `GET /api/auth/me` for the `XSRF-TOKEN` cookie, logs in
    over HTTP, and gives the session cookie and CSRF header to put on a `java.net.http` request
  - [ ] `AnalysesApiIntegrationTest` and `PresetsApiIntegrationTest`: run with
    `platform.auth.enabled=true` and send every request through a `TestSession`
  - [ ] `TradingPlatformApplicationTests` (MockMvc): keep the default (switch off) and add a test that
    `/api/analyses` answers 200 without a session and a `POST` works without a CSRF token, which
    proves the switch keeps today's behaviour
- **Tests**: `AuthApiIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, real PostgreSQL through
  `TestDatabaseConfiguration`, `platform.auth.enabled=true`), one test per point:
  - `GET /api/analyses` without a session: 401, `errorCode` `unauthorized`, no `Location` header;
  - `GET /api/auth/me` without a session: 401, and the response sets `XSRF-TOKEN`;
  - `POST /api/auth/login` without the CSRF header: 403 `invalid_csrf_token`;
  - wrong password, unknown user and disabled user: 401 `bad_credentials`, identical bodies;
  - a valid login: 200 with the user; the session cookie is `HttpOnly` and `SameSite=Lax`, and its
    ID differs from the one the client had before the login (session fixation); `/me` then answers
    200 with the same user;
  - with a session, `POST /api/presets` without the CSRF header: 403; with it: 201 (or what it
    answers today);
  - `POST /api/auth/logout`: 204; the old session cookie then gets 401 on `/api/analyses`;
  - `/`, a client route (`/runs`), `/actuator/health` and `/api/openapi` answer without a session;
  - no `UserDetailsService` bean exists (no generated password).

### WP4: API types, docs and version

- **Depends on**: WP2 (the API), WP3 (the final behaviour)
- **Files**: `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts`,
  `README.md`, `docs/coding-convention/backend-java-package-structure.md`, the three version files
- **Steps**:
  - [ ] `mise run api-types` against the backend with this change (the three `/api/auth` operations,
    `LoginRequest`, `CurrentUserDto`); the frontend type-check stays green
  - [ ] The docs in "Docs to update"
  - [ ] `mise run version:bump minor`
- **Tests**: `mise run check` (frontend type-check included).

## Tests

- Unit: `AuthenticationServiceTest` (identity core), `IdentityExceptionToApiExceptionMapperTest` (BFF).
- Integration (`:backend`, PostgreSQL through Testcontainers): `AuthApiIntegrationTest` covers every
  acceptance criterion of #80 with the switch on: no session gives 401 on `/api/**`, a POST without a
  valid CSRF token gives 403, logout invalidates the session, and with a session the API behaves as
  before (the analyses and presets integration tests now run logged in).
  `TradingPlatformApplicationTests` proves the default (switch off) is today's behaviour.
- Architecture: `ArchitectureTest` and `PersistenceArchitectureTest` unchanged and green: the new
  classes sit where the existing rules expect them.
- e2e: unchanged and green, since it runs with the default (switch off). Playwright's login (log in
  once, reuse the stored state) moves to #81, which builds the login page and turns the switch on in
  `e2e/start-platform.sh` (see "Out of scope").

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Run with Docker Compose" → "Notes", "Exposing it beyond localhost" | Still true while login is off by default. Say that login is being built (#78), with a link, instead of only "not built yet" |
| Text | `README.md`, "Development" → "Dev servers" (or a short "Login (in progress)" paragraph next to it) | `PLATFORM_AUTH_ENABLED=true` turns on the session login for the API: the endpoints, the 401/403 behaviour, the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header, `PLATFORM_SESSION_TIMEOUT`. Until #81 and #82, the UI cannot log in and no user exists, so it is for developing those |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" | `config/` holds the web and security configuration (`SpaWebConfig`, `SecurityConfig`) |
| Text | `artifact/frontend/openapi.json`, `src/shared/api/schema.d.ts` | Regenerated with `mise run api-types` |
| Screenshot | none | No page changes: the login page comes with #81 |

The CI table in the README does not change: no new job, and the new tests run in the existing build
job.

## Out of scope

- **Turning login on for good and removing `platform.auth.enabled`**: whichever of #81 (login page)
  and #82 (first admin) is merged last. Their plans must include it: drop the property and its README
  paragraph, run the e2e with login (Playwright logs in once through the login page or
  `/api/auth/login` and reuses the stored state), and retake no screenshot unless a page changes.
- The login page, the 401 redirect, the CSRF header in `client.ts` and the logout button: #81.
- Creating the first admin: #82. Forcing a password change: #83. Password policy: #84. Rate limit and
  lockout: #85.
- Closing open SSE streams at logout: #86.
- Permission checks on endpoints and the built-in roles: #87. Ending the sessions of a user who is
  disabled while logged in: #88.
- Authentication of actuator endpoints and runner callbacks: out of scope of #78.
- Sessions that survive a restart (Spring Session in PostgreSQL): not asked for; sessions stay in
  Tomcat's memory.
