# Plan: Create the first admin on first start, and turn login on for good

- **Issue**: #82 (Auth: create the first admin on first start)
- **Plan**: 1 of 1 for this issue; depends on: none (#79, #80 and #81 are merged)
- **Version bump**: major (docs/coding-convention/repository-versioning-and-releases.md): login
  becomes mandatory and `PLATFORM_AUTH_ENABLED` is removed, so every `/api/**` call without a session
  now answers 401. That is a breaking change of the REST API and the configuration: 3.9.0 → 4.0.0.

## Goal

A fresh installation (`mise run run`, `mise run dev` or Docker Compose) creates one `admin` account
with the built-in ADMIN role on its first start, so someone can log in. The password comes from
`PLATFORM_ADMIN_PASSWORD`; without it a random one is generated, printed to the log once and the
account is flagged `must_change_password`. Once any user exists the start does nothing, so restarts
never reset a password. With a first user guaranteed, login is turned on for good: the temporary
`platform.auth.enabled` switch from #80 is removed, as #80's and #81's plans left to the later of
#81 and #82. The end-to-end tests, the screenshots and the Compose smoke test log in as that first
admin.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: identity domain | `backend-java-package-structure.md`: the use case in `domain.identity.core.inbound`, its package-private `*Service` in `domain.identity.core.service`, no new adapter package (it uses the existing `persistence` and `password` ports). `backend-java-persistence.md` / `backend-database-naming.md`: the ADMIN role is seeded by a new migration `V3__…` in the identity migration folder (never edit `V1`/`V2`), names uppercase and quoted, qualified with `"IDENTITY"`, enum labels as the Java constant names. `backend-java-checkstyle.md` (suppressions in place, with a reason) and `backend-java-formatting.md` (Spotless). |
| Backend: BFF | `backend-java-package-structure.md`: security code stays in `bff.delegate.impl.config` (`bff_api_holds_no_security_code`); the API module holds no logic. |
| Backend: tests | `backend-java-persistence.md` "Tests": PostgreSQL through Testcontainers, no `JdbcClient`, no test-managed transaction; the identity conventions test (`IdentityPersistenceConventionsTest`) covers the new migration. |
| e2e | The existing Playwright setup (`e2e/playwright.config.ts`, `playwright.screenshots.config.ts`, `start-platform.sh`, `tests/global-setup.ts`); Prettier formatting checked in the e2e job. |
| deploy, CI | `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/smoke-test.sh` (run by the CI *images* job). No workflow change. |
| Whole repository | `repository-versioning-and-releases.md` (major bump with `mise run version:bump major`), `repository-dependency-hygiene.md` (no new dependency; remove what becomes unused), docs in English. |

## Design

### The ADMIN role (migration)

#87 plans the built-in roles "seeded by migration and not deletable". This issue needs only ADMIN,
so it seeds that one now and leaves ANALYST and VIEWER to #87:

- `V3__seed_admin_role.sql` in
  `domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/`:
  inserts into `"IDENTITY"."ROLES"` the row `ID = 'ADMIN'`, `NAME = 'ADMIN'`, `BUILT_IN = TRUE`,
  `DESCRIPTION = 'Everything, including users, roles and API keys'`, `PERMISSIONS` = every label of
  `"IDENTITY"."PERMISSION"` (cast to `"IDENTITY"."PERMISSION"[]`), `VERSION = 0`. A fixed ID, so the
  core can name the role without a lookup by name and #87 can seed its roles the same way. A comment
  in the migration says that a new `Permission` constant needs a migration that adds it to ADMIN.
- `RoleId.ADMIN` (a `public static final RoleId ADMIN = new RoleId("ADMIN")` on the core record),
  documented as the ID the migration seeds.

### First admin on start (identity core)

- `domain.identity.core.inbound.CreateFirstAdminUseCase` with one method,
  `void createFirstAdminIfNoUser()`, whose Javadoc states the contract below.
- `domain.identity.core.service.FirstAdminService` (`@Service`, package-private) implements it, with
  `@EventListener(ContextRefreshedEvent.class)` on a method that calls it, the same startup hook as
  `ImportExistingRunsService`, but synchronous: the admin exists before the start completes, and an
  invalid configuration fails the start. Two constructors as in `ImportExistingRunsService`: the
  `@Autowired` one takes `UserRepositoryPort`, `PasswordHasherPort`,
  `@Value("${platform.admin.username:admin}") String username` and
  `@Value("${platform.admin.password:}") String password`; the package-private one also takes the
  password generator, for the tests.
- Behaviour:
  1. `users.count() > 0`: do nothing. If a password is configured, log at INFO once
     "Users exist: PLATFORM_ADMIN_USERNAME and PLATFORM_ADMIN_PASSWORD are ignored", never the value.
  2. Otherwise build the user: `UserId.newId()`, `new Username(username)` (an invalid name throws
     `IdentityException(INVALID_USERNAME)`, which fails the start with its message), `enabled = true`,
     `failedLoginCount = 0`, `lockedUntil = empty`, `roleIds = {RoleId.ADMIN}`, `version = null`.
     - Configured password (not blank): hash it, `mustChangePassword = false`.
     - No password (unset or blank): generate one, hash it, `mustChangePassword = true`.
  3. `users.save(user)`. A `DuplicateKeyException` (another instance created the user at the same
     time) is logged at INFO and the start goes on; nothing is printed.
  4. Only after the save succeeded, for a generated password, log it once at WARN, as one block
     that is easy to find in a terminal or `docker compose logs`:
     `First start: created the admin user "admin" with the generated password <pw>. It is shown only
     this once; change it after the first login.` Never log a configured password.
- The plain password is only in memory: the user holds `PasswordHasherPort#hash` of it (the
  delegating `{bcrypt}` encoder), and nothing else stores it.
- `OneTimePasswordGenerator` (package-private, `domain.identity.core.service`, next to the service
  as `AnalysisEventHub` sits next to its service): 24 characters drawn with `SecureRandom` from
  `A-Z a-z 0-9` (about 142 bits), so it passes the coming password policy (#84: at least 12
  characters) and needs no quoting in a shell. #88 (generated one-time passwords for new users) can
  reuse it.
- No password policy check here: #84 adds it ("a configured first-admin password that breaks the
  policy stops startup"); this plan only names the place (`FirstAdminService`) in the code's Javadoc.

### Configuration

`application.properties`, in the "Login (#78)" block:

```properties
# The first admin, created on the first start when no user exists (#82). Without a password one is
# generated, printed to the log once, and the account must change it. Ignored once any user exists.
platform.admin.username=${PLATFORM_ADMIN_USERNAME:admin}
platform.admin.password=${PLATFORM_ADMIN_PASSWORD:}
```

### Login on for good (BFF and properties)

- Remove `platform.auth.enabled` from `application.properties` with its "Temporary" comment, and
  correct the `server.address` comment ("requires auth + HTTPS, which the platform does not have
  yet" → "requires HTTPS").
- `SecurityConfig#securityFilterChain`: drop the `@Value` parameter and the `else` branch; the CSRF,
  `CsrfCookieFilter` and `authorizeHttpRequests` rules always apply, unchanged. Update the class
  Javadoc (no switch any more).

### e2e and screenshots log in as the first admin

- `e2e/start-platform.sh`: export `PLATFORM_ADMIN_USERNAME=admin` and
  `PLATFORM_ADMIN_PASSWORD=e2e-test-password` (a comment: throwaway database, test-only) before
  starting the jar; remove the health wait and the seed step, and the `PLATFORM_AUTH_ENABLED`
  sentence of the header comment.
- Delete `e2e/seed-user.sql`, and the `throwaway-sql` subcommand of `scripts/postgres.sh` (its only
  user; its usage line and header comment too).
- `e2e/tests/support.ts`: `E2E_USER = 'admin'` (the password stays); comment: the first admin that
  `start-platform.sh` configures.
- `e2e/tests/global-setup.ts`: the comments say "the first admin" instead of "the seeded user"; the
  login retry stays (the admin is created during the start, the health check can answer a moment
  earlier).
- `e2e/playwright.config.ts`: drop `PLATFORM_AUTH_ENABLED: 'true'` from `webServer.env` and fix the
  two comments that mention the seed.
- `e2e/playwright.screenshots.config.ts`: drop the login-off override: use the base global setup
  (login and warm-up) and the base `storageState`; update the comment (the pages show the user menu
  of `admin`).
- `e2e/tests/warm-up.ts`: remove the default export (the screenshots' login-off setup), keep
  `warmUpApi`.
- `e2e/screenshots/login.shot.ts`: `test.use({ storageState: { cookies: [], origins: [] } })`, so the
  login page is shown logged out.
- `e2e/tests/auth.e2e.ts`: no logic change; it uses `E2E_USER`, so the header check now expects
  `admin`.

### Docker Compose

- `deploy/docker-compose.yml`, `platform.environment`:
  `PLATFORM_ADMIN_USERNAME: ${PLATFORM_ADMIN_USERNAME:-admin}` and
  `PLATFORM_ADMIN_PASSWORD: ${PLATFORM_ADMIN_PASSWORD:-}`.
- `deploy/.env.example`: both variables with a comment (first start only; empty → generated, see
  `docker compose -f deploy/docker-compose.yml logs platform`; the account must change it).
- `deploy/smoke-test.sh`: the env file sets `PLATFORM_ADMIN_PASSWORD=smoke-test-only-password`.
  `wait_for_platform` polls `/actuator/health` instead of `/api/analyses` (now 401). A `login`
  function: `GET /api/auth/me` with a curl cookie jar (`-c`/`-b`), `POST /api/auth/login` with
  `X-XSRF-TOKEN` from the jar; every API call uses the jar and, for the POST, the header. Log in
  after the start, after the database restart and after down/up (sessions live in memory), which
  also proves that a second start keeps the admin and its password. One new step: "First admin":
  the login works and `GET /api/auth/me` shows `admin` with the `ADMIN` role.

## Work packages

### WP1: ADMIN role and the first admin (backend core and migration)

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/migration/V3__seed_admin_role.sql` (new)
  - `artifact/backend/domain/identity/core/src/main/java/…/domain/identity/core/model/RoleId.java`
  - `…/domain/identity/core/inbound/CreateFirstAdminUseCase.java` (new)
  - `…/domain/identity/core/service/FirstAdminService.java` (new)
  - `…/domain/identity/core/service/OneTimePasswordGenerator.java` (new)
  - `artifact/backend/src/main/resources/application.properties` (the two `platform.admin.*` lines)
  - tests: `…/identity/core/src/test/…/service/FirstAdminServiceTest.java`,
    `…/service/OneTimePasswordGeneratorTest.java` (new),
    `…/identity/adapter/src/test/…/persistence/JpaIdentityRepositoryTest.java`
- **Steps**:
  - [x] Migration `V3__seed_admin_role.sql` with the ADMIN row and its comment
  - [x] `RoleId.ADMIN`
  - [x] `CreateFirstAdminUseCase`, `FirstAdminService`, `OneTimePasswordGenerator` as designed
  - [x] The `platform.admin.*` properties
- **Tests**:
  - `FirstAdminServiceTest` (fakes as in `AuthenticationServiceTest`): no user + configured password
    → one enabled user `admin`, role `ADMIN`, the hash matches the password, not flagged; no user +
    no password (and blank password) → flagged, the hash matches the generated password, the stored
    hash is not the plain password; a custom username is used; an invalid username throws
    `INVALID_USERNAME`; a user exists → nothing saved and the generator is not called; called twice
    on an empty store → one user, the generator called once; `DuplicateKeyException` on save →
    no exception out of the service and the password is not logged (the generator's value never
    reaches a log call: assert through a counting fake and a Logback `ListAppender` if the core's
    test classpath has Logback, otherwise through the integration test of WP2).
  - `OneTimePasswordGeneratorTest`: 24 characters, only `A-Za-z0-9`, two calls differ.
  - `JpaIdentityRepositoryTest`: after the migrations the ADMIN role exists (`findById(RoleId.ADMIN)`)
    with `builtIn` and every `Permission`; a user saved with `RoleId.ADMIN` round-trips. Adjust any
    existing assertion that expects an empty role table (`findAll`).
  - `IdentityPersistenceConventionsTest` (unchanged) checks the new migration's names and schema.

### WP2: Login always on (BFF and Spring Boot tests)

- **Depends on**: WP1
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/config/SecurityConfig.java`
  - `artifact/backend/src/main/resources/application.properties` (remove the switch, fix the
    `server.address` comment)
  - `artifact/backend/src/test/java/…/platform/TradingPlatformApplicationTests.java`,
    `AuthApiIntegrationTest.java`, `AnalysesApiIntegrationTest.java`,
    `PresetsApiIntegrationTest.java`, `TestSession.java`
  - `artifact/backend/src/test/java/…/platform/FirstAdminIntegrationTest.java` (new)
- **Steps**:
  - [ ] Remove the switch and the login-off branch; update the Javadoc
  - [ ] Remove `registry.add("platform.auth.enabled", …)` from the three integration tests and the
        switch from `TestSession`'s Javadoc
  - [ ] `TradingPlatformApplicationTests`: replace `keepsTodaysBehaviourWithLoginOff` with a test that
        `/api/analyses` without a session is 401 and a POST without a CSRF token is 403; split
        `keepsUnknownApiPathsAndMissingAssetsAsNotFound` (a missing asset stays 404; an unknown API
        path without a session is 401); the SPA and OpenAPI tests stay as they are
- **Tests**:
  - `FirstAdminIntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, its own
    Testcontainers database through `TestDatabaseConfiguration`, `OutputCaptureExtension`, no
    `platform.admin.password`): the log holds the generated-password line exactly once; the password
    taken from that line logs in as `admin` through `TestSession` and `/api/auth/me` shows the role
    `ADMIN`, every permission and `mustChangePassword = true`; calling
    `CreateFirstAdminUseCase#createFirstAdminIfNoUser` again changes nothing (same user, same
    version, same hash) and logs no second password line; `users.count()` stays 1.
    If the context is started before the extension captures, the agent captures the output another
    way (e.g. a Logback `ListAppender` registered in a static initializer) and says so in the PR.
  - A second class or nested configuration with `platform.admin.password` set: the configured
    password logs in, `mustChangePassword = false`, and the output contains neither the password
    nor a generated-password line.
  - The existing `AuthApiIntegrationTest`, `AnalysesApiIntegrationTest` and
    `PresetsApiIntegrationTest` pass without the switch (the admin the start creates does not get in
    their way: they create their own users).

### WP3: e2e and screenshots log in as the first admin

- **Depends on**: WP2
- **Files**: `e2e/start-platform.sh`, `e2e/seed-user.sql` (deleted), `scripts/postgres.sh`,
  `e2e/tests/support.ts`, `e2e/tests/global-setup.ts`, `e2e/tests/warm-up.ts`,
  `e2e/playwright.config.ts`, `e2e/playwright.screenshots.config.ts`,
  `e2e/screenshots/login.shot.ts`, `docs/screenshots/*.png` (all retaken)
- **Steps**:
  - [ ] The e2e changes listed in the design
  - [ ] `mise run e2e` passes
  - [ ] `mise run screenshots` retakes every screenshot; check each one shows the `admin` user menu in
        the header and otherwise the same page as before, and `login.png` the empty login form
- **Tests**: the whole e2e suite (`mise run e2e`), `auth.e2e.ts` in particular (login, wrong password,
  logout, redirect) as the configured admin.

### WP4: Docker Compose and the smoke test

- **Depends on**: WP2
- **Files**: `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/smoke-test.sh`
- **Steps**:
  - [ ] The Compose, `.env.example` and smoke-test changes listed in the design
  - [ ] Run `deploy/smoke-test.sh` locally if Docker is available (`mise run docker-build` first);
        otherwise CI's *images* job runs it
- **Tests**: `deploy/smoke-test.sh` (CI *images* job): the configured admin logs in after the first
  start, after a database restart and after down/up; the analysis, health and data checks run in
  that session.

### WP5: Docs and version

- **Depends on**: WP1–WP4
- **Files**: `README.md`, `gradle.properties`, `artifact/frontend/package.json`,
  `artifact/ta-runner/ta_runner/__init__.py` (through `mise run version:bump major`)
- **Steps**:
  - [ ] The README changes in "Docs to update"
  - [ ] `mise run version:bump major` (3.9.0 → 4.0.0)
  - [ ] `mise run check`
- **Tests**: none of its own; `mise run check`.

## Tests

- Unit (identity core): `FirstAdminServiceTest`, `OneTimePasswordGeneratorTest`.
- Repository (PostgreSQL, Testcontainers): `JpaIdentityRepositoryTest` (the seeded ADMIN role), the
  unchanged `IdentityPersistenceConventionsTest` and `V2IdentityVersionMigrationTest`.
- Spring Boot (PostgreSQL): `FirstAdminIntegrationTest` proves the issue's acceptance criteria: an
  empty database gets an admin who can log in, a second run makes no change, and a generated
  password appears in the log exactly once and is stored only as a hash.
  `TradingPlatformApplicationTests` and the existing API tests prove login is always on.
- e2e: the whole suite logs in as the configured first admin; screenshots retaken.
- Compose smoke test: the configured admin logs in on the first start and again after restarts.
- `mise run check` and CI (*Backend and frontend*, *End-to-end tests*, *Platform image and Compose
  smoke test*, *Version*).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md` "Quick start", new step "3. Log in" after "2. Start the platform" (renumber the later steps and the links to them, e.g. `#3-add-an-api-key`) | The first start creates the user `admin` and prints a generated password once in the terminal (quote the log line); log in at http://127.0.0.1:8080 with it. To choose the name and password instead, set `PLATFORM_ADMIN_USERNAME` and `PLATFORM_ADMIN_PASSWORD` before the first start. Later starts change nothing. A generated password is flagged to be changed (the change-password screen comes with #83). Lost it while it is still the only user: delete it (`DELETE FROM "IDENTITY"."USERS";` in the database) and restart to get a new one |
| Text | `README.md` "Run with Docker Compose" | `.env` may set `PLATFORM_ADMIN_USERNAME` / `PLATFORM_ADMIN_PASSWORD`; without them the password is in `docker compose -f deploy/docker-compose.yml logs platform`. "Exposing it beyond localhost needs authentication and HTTPS first…" becomes: login is always on; HTTPS is not built yet; the port is published on 127.0.0.1 only |
| Text | `README.md` new "Upgrading to 4.0.0" (above "Upgrading to 3.0.0") | Login is always on and `PLATFORM_AUTH_ENABLED` is gone; an installation without users gets the first admin on its next start (see Quick start); scripts that call the API need a session and the CSRF header (`/api/auth/me`, `/api/auth/login`, `X-XSRF-TOKEN`) |
| Text | `README.md` "Development" → "Login (in progress)" | Rename to "Login"; drop the switch, "until the first admin", and "Off, the platform behaves as before"; keep the login page, 401, CSRF and session bullets; the e2e tests log in as the first admin that `e2e/start-platform.sh` configures (`admin` / `e2e-test-password`), and so do the screenshots. Fix the anchor link `#login-in-progress` from the Compose section |
| Text | `README.md` "Dev servers" | One sentence: `mise run dev` needs a login too, with the same first admin |
| Text | `deploy/.env.example` | The two variables (WP4) |
| Text | `artifact/backend/src/main/resources/application.properties` | Comments (WP1, WP2) |
| Text | `docs/coding-convention/backend-java-persistence.md` | None: no rule changes; the ADMIN seed is data, not a convention |
| Screenshot | every `docs/screenshots/*.png` except `login.png`: `analyses`, `compare`, `dashboard`, `new-analysis`, `presets`, `reports`, `run-debates`, `run-decision`, `run-live`, `run-reports`, `run-stats`, `run-table-chart`, `settings` | Retaken: the header now shows the user menu of `admin`. Same pages, states and replayed data as before (`mise run screenshots`, 1440×900, dark) |
| Screenshot | `docs/screenshots/login.png` | Retaken logged out (`login.shot.ts` with an empty storage state); it should look the same |

The CI table does not change: no new job. `openapi.json` and the frontend API types do not change:
no endpoint changes.

## Out of scope

- The change-password screen, the 403 for a flagged user and the endpoint to change a password:
  #83. Until then the flagged admin can use the app; the flag is stored and returned by `me`.
- The password policy, including stopping the start for a weak configured password: #84.
- ANALYST and VIEWER, permission checks on endpoints, and keeping ADMIN's permissions complete when
  `Permission` grows: #87 (the V3 comment points at it).
- The Ansible `app` role passing `PLATFORM_ADMIN_USERNAME` / `PLATFORM_ADMIN_PASSWORD`: #76 has no
  Ansible code yet; its app role takes them from `deploy/.env` like the other variables.
- Resetting a lost admin password while other users exist: #88 (admin reset).
- The frontend: no change (the login page, user menu and `mustChangePassword` in the store exist).
