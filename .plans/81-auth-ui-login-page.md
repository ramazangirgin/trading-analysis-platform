# Plan: Auth UI: login page, 401 handling, logout and current user

- **Issue**: #81 (Auth UI: login page, 401 handling, logout and current user)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

With login turned on (`PLATFORM_AUTH_ENABLED=true`, from #80), the Vue app can be used: opening
any page while logged out leads to a login page, logging in returns to the page first asked for,
the header shows the current username with a logout action, and an expired session sends the user
back to the login page instead of showing errors. The client sends the CSRF header on every unsafe
request. The current user and their permissions are kept in one store, ready for permission-based
UI hiding later (#87).

Login stays behind the temporary switch, off by default: with it off, the app looks and works
exactly as before (no login page shown, no user menu). Turning login on for good and removing the
switch is #82's job (the first admin), which is merged after this one.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Frontend (`artifact/frontend`) | `docs/coding-convention/frontend-folder-structure.md`: a new feature `features/auth/` (`index.ts` its only public entry, `api.ts` the only file calling `@/shared/api/client`, `store.ts` Pinia, `components/`, `model/` pure TS with `*.spec.ts`); the route component in `pages/LoginPage.vue`, the route in `app/router/index.ts`; `shared` knows no feature and no router, so the 401 hook in `shared/api/client.ts` is a registration function the `app` wires up; types in `shared/api/types.ts` derived from `schema.d.ts`; translations in both `shared/i18n/locales/en.json` and `tr.json` (`locales.spec.ts` checks they have the same keys). Prettier, ESLint boundaries, Knip, `vue-tsc` through `mise run check` |
| End-to-end tests (`e2e/`) | The existing Playwright setup: one platform for all workers started by `start-platform.sh`, tests independent and parallel (no shared state they own), Prettier and type-check of `e2e/`; screenshots with `e2e/screenshots/*.shot.ts` and `shot()` at 1440×900, dark theme |
| Scripts (`scripts/postgres.sh`) | The script's existing subcommand style (`throwaway`, `throwaway-stop`), Docker or Podman through `$engine` |
| Docs | Root `README.md` ("Login (in progress)" section), English only |
| Backend | No change. The endpoints, 401/403 error codes, CSRF cookie and header exist since #80 |

## Design

### Backend contract it builds on (no change)

- `GET /api/auth/me`: 200 `CurrentUserDto` (`id`, `username`, `roles`, `permissions`,
  `mustChangePassword`) or 401 `unauthorized`; it always sets the `XSRF-TOKEN` cookie. With the
  switch **off** it answers 401 too (there is never a session), while every other route answers
  normally.
- `POST /api/auth/login` (`username`, `password`): 200 `CurrentUserDto`, or 401 `bad_credentials`
  for both an unknown user and a wrong password. It needs the CSRF header like every unsafe request,
  and rotates the session ID and the CSRF token.
- `POST /api/auth/logout`: 204, ends the session.
- Every other `/api/**` route without a session: 401 `unauthorized`, no redirect.

### Shared HTTP client (`shared/api/`)

- `shared/api/csrf.ts` (new): `csrfToken(cookie = document.cookie): string | null` reads the
  `XSRF-TOKEN` cookie (URL-decoded). Pure, with `csrf.spec.ts`.
- `shared/api/client.ts`:
  - Builds headers from `init.headers` as well (today it drops them), and adds
    `X-XSRF-TOKEN: <cookie>` to every request whose method is not `GET`/`HEAD`/`OPTIONS`, when the
    cookie exists. The cookie is read on every request, since login rotates it.
  - `onUnauthorized(handler: () => void)`: registers one handler. On a 401 the client calls it and
    still throws the `ApiError`, except for requests made with a new option `{ skipUnauthorized:
    true }` (the second parameter of `request`, beside `init`), which `authApi` uses for `me` and
    `login`: their 401 is an answer, not an expired session.
- With the switch off, no route but `me` answers 401 and the CSRF header is ignored, so nothing
  changes.

### Auth feature (`features/auth/`)

- `api.ts`: `authApi.me()`, `authApi.login(username, password)`, `authApi.logout()`, all through
  `request`; `me` and `login` with `skipUnauthorized`.
- `store.ts`: `useSessionStore` (Pinia, setup style like `useCatalogStore`):
  - state `user: CurrentUser | null`, `loaded: boolean`;
  - `load()`: calls `me` once (a pending promise shared like the catalog store's); 200 → `user`,
    401 → `user = null`; other errors leave `user = null` and are rethrown to the caller;
  - `login(username, password)`: sets `user` from the answer;
  - `logout()`: calls the endpoint, then `window.location.assign('/login')`: a full page load drops
    every in-memory store and page state, so nothing protected survives in memory;
  - `clear()`: `user = null` (used by the 401 handler);
  - getters `username`, `permissions` (a `Set`), `can(permission: string): boolean` — the one place
    the UI asks later (#87). Nothing hides anything by permission yet.
- `model/redirect.ts`: `safeRedirect(value: unknown): string` returns the value when it is a
  same-app path (a string starting with `/`, not `//` or `/\`, not `/login`), else `/`. Guards
  against an open redirect through `?redirect=`. With `redirect.spec.ts`.
- `components/LoginForm.vue`: Naive UI `NForm` with `NInput` username and password
  (`type="password"`, `autocomplete` `username` / `current-password`), a submit `NButton` with a
  loading state, `NAlert` for errors. Submits on Enter. On `bad_credentials` it shows one message
  ("Wrong username or password.") that never says which one was wrong; other errors use the
  existing `errors.<code>` translations (`network_error`, `unexpected_error`). Emits `logged-in`.
- `components/UserMenu.vue`: the username and a logout action (an `NDropdown` on the username, or
  a text button beside it; one action for now, so the change-password entry of #83 fits in later).
  Rendered only when the store has a user.
- `index.ts`: exports `useSessionStore`, `safeRedirect`, `LoginForm`, `UserMenu`.

### Login page and routing (`pages/`, `app/`)

- `pages/LoginPage.vue`: centred card with the app title and `LoginForm`; on `logged-in` it
  `router.replace(safeRedirect(route.query.redirect))`. When the store already has a user (an
  open session), it goes there at once.
- `app/router/index.ts`: route `{ path: '/login', name: 'login', component: LoginPage, meta: {
  bare: true } }`. A `beforeEach` guard awaits `useSessionStore().load()` once before the first
  navigation (errors other than 401 do not block navigation).
  - No guard redirects on "no user" by itself: with the switch off there is never a user, and the
    app must keep working. The redirect comes from the 401 handler, which only fires when login is
    on (see below). Every protected page loads data on mount, so it is reached at once.
- `app/main.ts`: after `use(router)`, registers `onUnauthorized`: clear the session store and, unless
  the current route is already `login`, `router.replace({ name: 'login', query: { redirect:
  router.currentRoute.value.fullPath } })`. Several 401s at once lead to one navigation (the guard
  is a no-op when already on `login`).
- `app/App.vue`: with `route.meta.bare` the header shows only the brand and the locale switcher
  (no nav, no user menu); otherwise the header gets `UserMenu` beside `LocaleSwitcher`.
- Back button after logout: logout is a full load of `/login`. Going back loads the SPA afresh with
  no session; the page's first request answers 401 and the handler returns to `/login`, so no
  protected data is shown. With login on, Spring Security sends `Cache-Control: no-store` on API
  responses, so the browser does not show them from its cache either.

### Types and translations

- `shared/api/types.ts`: `export type CurrentUser = Present<Schemas['CurrentUserDto']>`.
- `en.json` / `tr.json`: a new `login` block (`title`, `username`, `password`, `submit`,
  `badCredentials`), `nav.logout`, and `errors.bad_credentials` (same text as `login.badCredentials`
  or reused through it), `errors.unauthorized` ("Your session has ended. Log in again."), with Turkish
  translations of the same keys.

### End-to-end tests with login on

The e2e platform runs with login **on**; the screenshots keep it **off**, so the existing README
screenshots stay valid.

- `scripts/postgres.sh`: a new subcommand `throwaway-sql <container> <file>` that runs the file with
  `psql -v ON_ERROR_STOP=1 -U platform -d platform` inside the container (`$engine exec -i`).
- `e2e/seed-user.sql` (new): inserts one enabled user `e2e` (not `must_change_password`) into
  `"IDENTITY"."USERS"` with a fixed `{bcrypt}` hash of a fixed test password, and a comment naming
  that password and why it is safe (throwaway database, test-only). Uppercase quoted names as in the
  identity migrations. No role row: no endpoint checks permissions yet (#87).
- `e2e/start-platform.sh`: when `PLATFORM_AUTH_ENABLED=true`, after starting the jar it waits for
  `/actuator/health` (curl, up to the same 120 s), then runs `throwaway-sql` with the seed. Flyway
  has created the tables by then.
- `e2e/playwright.config.ts`: `webServer.env` gets `PLATFORM_AUTH_ENABLED: 'true'`; `use.storageState`
  points at `e2e/.auth/user.json` (gitignored).
- `e2e/tests/global-setup.ts`: after the warm-up, logs in over the API: `GET /api/auth/me` (CSRF
  cookie), `POST /api/auth/login` with `X-XSRF-TOKEN`, retrying the login for up to 30 s (the seed
  runs right after the health check that Playwright also waits for), and saves the storage state.
- `e2e/tests/support.ts`: `csrfHeaders(request)` reads `XSRF-TOKEN` from
  `request.storageState()` and returns `{ 'X-XSRF-TOKEN': … }`; `completedAnalysis` and every other
  unsafe API call in the tests and shots send it.
- `e2e/tests/auth.e2e.ts` (new): runs in a fresh context (`test.use({ storageState: { cookies: [],
  origins: [] } })`) and logs in through the form itself, never with the shared state, because its
  logout ends that session and would log the other parallel tests out:
  1. open `/reports` logged out → URL is `/login?redirect=%2Freports`;
  2. wrong password → the "Wrong username or password." alert; the same message for an unknown
     username;
  3. right password → back on `/reports`, the header shows `e2e`;
  4. logout → on `/login`; `page.goBack()` → ends on `/login` again, and no report list is visible.
- `e2e/playwright.screenshots.config.ts`: `webServer.env` sets `PLATFORM_AUTH_ENABLED: 'false'`, no
  `storageState`, and a global setup that only warms up (split `global-setup.ts` into the warm-up and
  the login so the screenshots reuse the warm-up).
- `e2e/screenshots/login.shot.ts` (new): `/login` with the form empty → `docs/screenshots/login.png`.

## Work packages

### WP1: HTTP client: CSRF header and 401 hook

- **Status**: done
- **Depends on**: none
- **Files**: `artifact/frontend/src/shared/api/csrf.ts`, `csrf.spec.ts`, `client.ts`,
  `client.spec.ts` (new), `types.ts`
- **Steps**:
  - [x] `csrfToken()` reading and decoding the `XSRF-TOKEN` cookie
  - [x] `request` merges `init.headers`, adds `X-XSRF-TOKEN` on unsafe methods, takes the
        `skipUnauthorized` option, calls the `onUnauthorized` handler on a 401
  - [x] `CurrentUser` type
- **Tests**: `csrf.spec.ts` (no cookie, other cookies only, encoded value); `client.spec.ts` with a
  stubbed `fetch` (`vi.stubGlobal`): header on POST/PUT/DELETE and not on GET, merged caller
  headers, the handler called on 401 and not with `skipUnauthorized`, the `ApiError` still thrown

### WP2: Auth feature, login page, routing and header

- **Status**: done
- **Depends on**: WP1
- **Files**: `artifact/frontend/src/features/auth/{index.ts,api.ts,store.ts}`,
  `features/auth/model/redirect.ts`, `redirect.spec.ts`, `features/auth/store.spec.ts` (new),
  `features/auth/components/{LoginForm.vue,UserMenu.vue}`, `pages/LoginPage.vue`,
  `app/router/index.ts`, `app/main.ts`, `app/App.vue`, `shared/i18n/locales/{en,tr}.json`
- **Steps**:
  - [x] `authApi`, `useSessionStore` with `load`, `login`, `logout`, `clear`, `can`
  - [x] `safeRedirect`
  - [x] `LoginForm`, `UserMenu`, `LoginPage`, the `/login` route with `meta.bare`
  - [x] the one-time `load()` guard and the `onUnauthorized` wiring in `main.ts`
  - [x] the header in `App.vue`: bare on the login page, `UserMenu` otherwise
  - [x] translations in both locales
- **Tests**: `redirect.spec.ts` (`/reports` kept, `//evil.example`, `/\evil`, `https://…`,
  `/login`, arrays and missing values → `/`); `store.spec.ts` with a fresh Pinia and a mocked
  `authApi` (`load` once for parallel callers, 401 → no user, `can` from permissions, `clear`);
  `locales.spec.ts` keeps both locales in step

### WP3: End-to-end tests with login on, and the login screenshot

- **Status**: done
- **Depends on**: WP2
- **Files**: `scripts/postgres.sh`, `e2e/seed-user.sql`, `e2e/start-platform.sh`,
  `e2e/playwright.config.ts`, `e2e/playwright.screenshots.config.ts`, `e2e/tests/global-setup.ts`
  (and a warm-up module split from it), `e2e/tests/support.ts`, `e2e/tests/*.e2e.ts` where they make
  unsafe API calls, `e2e/tests/auth.e2e.ts`, `e2e/screenshots/login.shot.ts`,
  `e2e/screenshots/*.ts` where they make unsafe API calls, `.gitignore` (`e2e/.auth/`),
  `docs/screenshots/login.png`
- **Steps**:
  - [x] `throwaway-sql` in `postgres.sh` and the seed file
  - [x] the seed step in `start-platform.sh`, only with login on
  - [x] login in the global setup, the storage state in the config, the CSRF header helper
  - [x] `auth.e2e.ts`
  - [x] the screenshot config with login off; `login.shot.ts`; take `login.png` with
        `mise run screenshots login`
- **Tests**: `mise run e2e` passes with login on: the existing tests (logged in through the stored
  state) and `auth.e2e.ts` (login, wrong password, redirect after login, logout, back button)

### WP4: Docs

- **Status**: done
- **Depends on**: WP2, WP3
- **Files**: `README.md`
- **Steps**:
  - [x] update "Login (in progress)" (see Docs to update)
- **Tests**: none (docs)

## Tests

- Frontend unit (Vitest, `mise run test` and the build): `csrf.spec.ts`, `client.spec.ts`,
  `redirect.spec.ts`, `store.spec.ts`, `locales.spec.ts`.
- `mise run check`: ESLint boundaries (the new feature entered through `index.ts`, only
  `features/auth/api.ts` imports the client, `shared` imports no feature), Prettier, Knip, type-check
  of the frontend and `e2e/`.
- End-to-end (`mise run e2e`, CI's *End-to-end tests* job), which proves the issue's acceptance
  criteria with login on: logged-out access goes to the login page and login returns to it; a wrong
  password and an unknown username give the same message; logout returns to the login page and the
  back button shows no protected data; every existing flow still works with a session and CSRF.
- Login off (the default) stays as before: the screenshots run with it off and need no retake except
  the new login page.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Development" → "Login (in progress)" | The UI now has a login page (`/login`), returns to the requested page after login, shows the username with a logout action in the header, and goes back to the login page when the session ends (any 401). It sends `X-XSRF-TOKEN` itself. Still: no user exists until the first admin (#82), so the switch is for development; say how the e2e tests run with login on (a seeded `e2e` user in their throwaway database). Replace "Until the login page (#81) and the first admin (#82) exist" with "Until the first admin (#82) exists" |
| Text | `README.md`, the e2e row of the CI table or the "Tasks" comment for `mise run e2e`, if it describes what the tests cover | Mention the login flow and that the tests run with login on, only if the existing text lists the flows |
| Screenshot | `docs/screenshots/login.png`, new | The login page, empty form, 1440×900 dark (`e2e/screenshots/login.shot.ts`), no data needed; shown in the "Login (in progress)" section |
| Screenshot | the other `docs/screenshots/*.png` | None retaken: with login off (the default, and how screenshots are taken) the header is unchanged |

## Out of scope

- Turning login on for good, removing `platform.auth.enabled`, and logging the e2e tests in as the
  configured admin instead of the seeded `e2e` user: #82 (first admin), merged after this plan. It
  removes `e2e/seed-user.sql`, the `throwaway-sql` call and the screenshot config's login-off
  override, and then retakes every screenshot whose header shows the user menu.
- The change-password screen and the `mustChangePassword` redirect: #83 (the store already keeps the
  flag).
- Hiding UI by permission: #87 (`can()` is ready, nothing uses it yet).
- Closing open SSE streams at logout: #86 (the full page load at logout already closes them in this
  tab).
- Login rate limit and lockout messages: #85.
- Any backend change.

## Open questions

None. Decided with the developer: #81 goes first and keeps the switch; e2e seeds a test user in its
own throwaway database (SQL against the built jar's PostgreSQL, since a test-only bean would not be
in the production jar), and #82 removes the seed.
