# TradingAgents Platform

[![CI](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml)

A web platform for running and managing [TradingAgents](https://github.com/TauricResearch/TradingAgents)
analyses without modifying its code: start an analysis from the browser, watch the agents work live,
then read the decision, the reports and the debates, and export them. The roadmap is the
[GitHub issues](https://github.com/ramazangirgin/trading-analysis-platform/issues);
[docs/event-protocol.md](docs/event-protocol.md) describes the runner contract.

![An analysis: agent pipeline and the final decision](docs/screenshots/run-decision.png)

| Artifact | Path | Stack |
|---|---|---|
| Platform (backend + UI, one jar) | `artifact/backend`, `artifact/frontend` | Java 25, Spring Boot 4.1 · Vue 3.5, Vite, Naive UI |
| Runner (installed into the TradingAgents runtime) | `artifact/ta-runner` | Python 3.12, uv, TradingAgents `v0.5.1` |
| Docker images and Compose setup | `deploy/`, `artifact/ta-runner/Dockerfile` | Temurin 25 JRE · PostgreSQL 18 · docker-socket-proxy |
| Server deployment | `deploy/server/` | Ansible over SSH |
| End-to-end tests (not shipped) | `e2e/` | Playwright, against the built jar |

Two ways to run it: on your machine with `mise run run` ([Quick start](#quick-start)), or with
Docker Compose, where each analysis runs in its own container and the data is kept in PostgreSQL
([Run with Docker Compose](docs/docker-compose.md)).

## Quick start

### 1. Prerequisites

- [mise](https://mise.jdx.dev/) — it installs the rest: Java 25 (Temurin) and
  [uv](https://docs.astral.sh/uv/), pinned in [`mise.toml`](mise.toml), into its own directory.
  Install it with `curl https://mise.run | sh` (or `brew install mise`).
- An API key for at least one LLM provider (OpenAI, Anthropic, Google, DeepSeek, xAI, Mistral,
  Groq, …), or a local [Ollama](https://ollama.com/), which needs no key.
- Docker or Podman (`mise run setup` installs one when there is none: Docker on Ubuntu or Debian,
  Podman on macOS with Homebrew; `PLATFORM_INSTALL_ENGINE=0` skips that). Under `mise run`, a
  running Podman machine's socket is set as `DOCKER_HOST` unless it is set already
  (`scripts/container-env.sh`); a Gradle daemon started before that needs `./gradlew --stop`. `mise run run` and
  `mise run dev` start a local PostgreSQL 18 container
  (`mise run db`; its data is in the volume `trading-analysis-platform-db`; `mise run db:stop` stops
  it, `mise run db:reset` deletes its data). To use your own PostgreSQL 18 server instead, set
  `PLATFORM_DB_HOST`, `PLATFORM_DB_PORT`, `PLATFORM_DB_NAME`, `PLATFORM_DB_USER` and
  `PLATFORM_DB_PASSWORD`; then no container is started if `PLATFORM_DB_HOST` is another host. For a
  server on `localhost` also set `PLATFORM_DB_CONTAINER=false`, else the container is started and
  needs the port free (Docker is still needed for the backend tests).
- Nothing else: Node.js and pnpm are downloaded by the build, Python 3.12 by uv.

### 2. Start the platform

```sh
git clone https://github.com/ramazangirgin/trading-analysis-platform.git
cd trading-analysis-platform
mise trust && mise install   # once: Java 25 and uv for this repository
mise run setup               # once: ta-runner (downloads TradingAgents), the UI packages, a container engine if missing
mise run run                 # builds the app if needed and serves it on :8080
```

Open **http://127.0.0.1:8080**. One process serves both the UI and the API; keep the terminal open
while you use it, and stop it with Ctrl+C. The first start takes a few minutes (Gradle's
dependencies, Node, building the UI and the backend); after that it starts in seconds, and after a
`git pull` it rebuilds only what changed. Analyses, presets and keys live on disk
([Where things live](docs/configuration.md#where-things-live)), so they survive restarts.

If the platform stops while an analysis runs (a crash, a `kill`), the analysis keeps going; the
restarted platform follows it again, or records its result if it finished meanwhile, and starts
queued analyses again. Ctrl+C in the terminal stops the running analyses too.

Changing the code instead? See [Development](docs/development.md) — it runs the UI with hot reload.

Next: [log in, add an API key and start an analysis](docs/user-guide.md#3-log-in).

## Documentation

| File | What it covers | Who reads it |
|---|---|---|
| [docs/user-guide.md](docs/user-guide.md) | Quick start steps 3 to 8: log in and users, API keys, starting an analysis, watching and sharing it, past analyses, reports and export, language | Users |
| [docs/docker-compose.md](docs/docker-compose.md) | Running the platform, PostgreSQL and the analyses as containers, data, security notes, upgrading between major versions | Operators |
| [docs/configuration.md](docs/configuration.md) | Where the platform keeps its data, and the LLM prices | Users, operators |
| [docs/login-and-backend.md](docs/login-and-backend.md) | The session login, password policy, lockout, public routes, users, roles, permissions and the endpoints behind them | Operators, developers |
| [docs/development.md](docs/development.md) | Dev servers, the `mise` tasks, coding conventions, before pushing, ta-runner, testing the Docker runner | Developers |
| [docs/ci-and-releases.md](docs/ci-and-releases.md) | The CI jobs and the rules on `main`, dependency updates, versioning and releases | Developers |
| [docs/agentic-development.md](docs/agentic-development.md) | Taking an issue to a reviewed pull request with agents | Developers |
| [docs/event-protocol.md](docs/event-protocol.md) | The contract between the backend and ta-runner | Developers |
| [docs/coding-convention/README.md](docs/coding-convention/README.md) | Where each kind of class, component or module belongs, and the rules the build checks | Developers |
| [deploy/server/README.md](deploy/server/README.md) | Setting up an Ubuntu server over SSH and deploying the Compose stack on it | Operators |

<<<<<<< HEAD
Open **http://localhost:5173** (not :8080) while developing. UI changes show up immediately; backend
changes need a restart of `mise run dev`. Nothing listens on 5173 unless `mise run dev` is running —
to just use the app, `mise run run` is enough. `mise run dev` needs a login too, with the same first
admin ([Quick start](#3-log-in)).

### Login

The backend has a server-side session login ([#78](https://github.com/ramazangirgin/trading-analysis-platform/issues/78)),
always on. The first start creates the first admin ([Quick start](#3-log-in)). The UI has a login
page (`/login`):

![The login page](docs/screenshots/login.png)

- Opening any page without a session leads to `/login?redirect=<page>`; after login the UI returns
  to the requested page. The header shows the username with a logout action, which ends the session
  and loads `/login` afresh. When the session ends (any 401), the UI goes back to the login page.
- When a run's live stream closes because the session ended, the run page goes to the login page
  and returns to the run after login.
- The UI sends the CSRF header `X-XSRF-TOKEN` itself on every unsafe request.
- An endpoint is checked in this order: 401 without a session, then 400 from request validation,
  then 403 `forbidden` from the endpoint's permission. A caller without the permission who sends an
  invalid body therefore gets 400 with the validation errors; only a valid request reaches the
  permission check.
- The end-to-end tests log in as the first admin that `e2e/start-platform.sh` configures
  (`admin` / `e2e-test-password`), and so do the screenshots.

Changing the password: the user menu → **Change password** opens `/password`. A user whose
password is flagged to be changed (a generated or one-time password) is sent there right after
login and can do nothing else until they set a new one. A change clears the flag and logs the
user's other sessions out; the current session continues.

![The change-password page](docs/screenshots/change-password.png)

Investment disclaimer: the login page included, every page shows a muted line under the header,
and the public About page (`/about`) shows the full text (see [Log in](#3-log-in)). The wording is
`app.disclaimer` in the translation files, shown by the `DisclaimerNotice` component.

![The About page](docs/screenshots/about.png)

The backend:

- `POST /api/auth/login` (`username`, `password`) answers 200 with the user, their roles and
  permissions, or 401 `bad_credentials` or 429 `too_many_login_attempts`; `GET /api/auth/me` returns the current user, or 401
  `unauthorized`; `POST /api/auth/logout` ends the session (204).
- `POST /api/auth/password` (`currentPassword`, `newPassword`) answers 200 with the user (flag
  cleared), or 400 `wrong_current_password` / `password_unchanged` / `weak_password`. The last one
  carries `params.reasons` (`too_short`, `too_long`, `common`, `same_as_username`), `minLength` and
  `maxLength`. A session whose password must be changed gets 403 `password_change_required` on
  every `/api/**` route but `me`, `password` and `logout`.
- The password policy follows NIST SP 800-63B: 12 to 128 characters (counted as Unicode code
  points), no composition rules, not one of the 10,000 most common passwords (bundled from
  [SecLists](https://github.com/danielmiessler/SecLists), so nothing leaves the machine) and not
  the username. The minimum is set with `PLATFORM_PASSWORD_MIN_LENGTH` (8 to 64, default 12).
- Password guessing is slowed down ([#85](https://github.com/ramazangirgin/trading-analysis-platform/issues/85)).
  After 5 wrong passwords in a row an account is locked for 15 minutes. While it is locked, every
  login answers the same 401 `bad_credentials` as a wrong password, even with the right one, so a
  locked account cannot be told from a wrong guess or an unknown user. A locked account is not
  counted further, and a successful login resets the count. Each client address may also fail 20
  logins in 5 minutes; after that it gets 429 `too_many_login_attempts` with `Retry-After` until
  the window frees up. The address is the one of the connection (`X-Forwarded-For` is not trusted),
  so under Docker Compose all clients may share the bridge gateway's address and the limit applies
  to them together. The counts of the IP limit live in memory like the sessions, so a restart
  clears them. The settings, all checked at startup:

  | Variable | Default | Range |
  |---|---|---|
  | `PLATFORM_LOGIN_MAX_FAILED_ATTEMPTS` | `5` | 1 to 100 |
  | `PLATFORM_LOGIN_LOCKOUT_DURATION` | `15m` | 1 minute to 24 hours |
  | `PLATFORM_LOGIN_IP_MAX_FAILED_ATTEMPTS` | `20` | 1 to 10,000 |
  | `PLATFORM_LOGIN_IP_WINDOW` | `5m` | 1 minute to 24 hours |

- Two routes are open to visitors without a session or a CSRF token, and only `GET`:
  - `GET /api/public/analyses?page=0&size=12` answers 200 with `{"items", "hasMore"}`, the partial
    analyses newest first, each with `id`, `ticker`, `analysisDate`, `rating` and an `excerpt` (the
    start of the first shown section, null when there is none). `page` is at least 0 and `size`
    1 to 24, or the answer is 400 `invalid_request`.
  - `GET /api/public/analyses/{id}` answers 200 with `id`, `ticker`, `analysisDate`, `rating`,
    `sections` (the shown report sections, keyed like the report: `market_report`) and
    `lockedSections` (the keys of the report sections and debate speakers that are cut, e.g.
    `sentiment_report`, `bull`; no text, sources, prices or owner). Anything but a partial
    analysis, a private or public one included, answers 404 `not_found`.
  - Each client address may make 60 requests per minute to `/api/public/**`; after that it gets
    429 `too_many_requests` with `Retry-After` until the window frees up. The address and the
    memory of the counts are as for the login limit. Every other route under `/api` still answers
    401 to a visitor, and `POST /api/public/**` is not open. The settings, checked at startup:

  | Variable | Default | Range |
  |---|---|---|
  | `PLATFORM_PUBLIC_RATE_LIMIT_MAX_REQUESTS` | `60` | 1 to 10,000 |
  | `PLATFORM_PUBLIC_RATE_LIMIT_WINDOW` | `1m` | 1 second to 24 hours |
  | `PLATFORM_ANALYSIS_PREVIEW_SECTIONS` | `market_report` | comma separated report sections (`market_report`, `sentiment_report`, `news_report`, `fundamentals_report`, …); an unknown name fails startup |
  | `PLATFORM_ANALYSIS_PREVIEW_EXCERPT_LENGTH` | `400` | 1 to 5,000 characters of the first shown section on a list card |

- Admins manage accounts through `/api/users`. Reading needs `user:read`, every change
  `user:manage`; without the permission the answer is 403 `forbidden`. A user has no hard delete: an
  admin disables the account instead. A `UserDto` has the `id`, `username`, `roles` (`id` and `name`,
  sorted by name), `enabled`, `mustChangePassword`, `locked` (an expired lock reads as not locked),
  `lockedUntil`, `lastLoginAt` (null until the first successful login), `createdAt`, `updatedAt` and
  `version`.
  - `GET /api/users` lists the users sorted by username, ignoring case; `GET /api/users/{id}`
    gives one (404 `user_not_found`).
  - `POST /api/users` with `{"username", "roleIds", "password"}` creates an enabled user and answers
    201 `{"user", "oneTimePassword"}`. A blank or missing `password` means "generate one": it is
    returned once, in `oneTimePassword`, and never again. An entered password is not echoed
    (`oneTimePassword` is null). Either way the user must change it at the first login
    (`mustChangePassword`, 403 `password_change_required` on other routes). Errors: 400
    `invalid_username`, `weak_password` (with `params.reasons`), `unknown_role` (`params.roleId`) or
    `invalid_request`; 409 `username_taken` (also in another case).
  - `PATCH /api/users/{id}` with `{"roleIds", "enabled", "version"}` changes the roles, the status
    or both; a field that is left out stays as it is, and one of `roleIds` and `enabled` is
    required (400 `invalid_request`). `version` is optional: without it the write applies to what is
    stored, with a stale one the answer is 409 `concurrent_update`. It answers 200 with the `UserDto`,
    400 `unknown_role`, 404, or one of the guard rails below.
  - `POST /api/users/{id}/password-reset` with `{"password"}` (blank or missing: generate one) gives
    the user a new one-time password and answers 200 `{"oneTimePassword"}` (null when the admin
    entered it); 400 `weak_password`; 404. The user must change it at the next login, and a reset
    also clears a lockout. It does not enable a disabled user.
  - `POST /api/users/{id}/unlock` unlocks an account early. It answers 204 (also when the account
    is not locked) or 404 `user_not_found`.
  - **Guard rails** (409): an admin cannot disable themselves (`cannot_disable_self`) or remove
    their own ADMIN role (`cannot_remove_own_admin`), and nobody can disable or demote the last
    enabled ADMIN (`last_admin`), so the platform always has an admin. The check and the write run
    under one lock, which is enough for one backend instance.
  - **Sessions end at once**: when a user is disabled, when their password is reset (also an
    admin's own: they log in again with the one-time password), and when their roles change. The
    next API request of such a session gets 401, and its open event streams close; a request for
    the UI (a page or a script) is redirected to itself and loads without the session, so the UI
    goes to the login page instead of failing to load. An admin who changes
    their own roles keeps their current session with its old permissions until the next login or
    the next change to a role (a permission change applies to the next request, see `/api/roles`).
  - The UI's **Admin → Users** page (`/admin/users`) uses these endpoints, explains a blocked guard
    rail before the request, and shows the one-time password once.
- Admins manage roles through `/api/roles` ([#90](https://github.com/ramazangirgin/trading-analysis-platform/issues/90)).
  Reading needs `user:read` or `role:manage` (the user forms read the roles), every change
  `role:manage`; without the permission the answer is 403 `forbidden`. A `RoleDto` has the `id`,
  `name`, `builtIn`, `description`, `permissions` (keys, sorted), `userCount` (the users, enabled or
  not, that hold the role) and `version`.
  - `GET /api/roles` lists the roles sorted by name; `GET /api/roles/{id}` gives one (404
    `role_not_found`).
  - `POST /api/roles` with `{"name", "description", "permissions"}` creates a custom role and
    answers 201 with the `RoleDto` and `Location: /api/roles/{id}`. `permissions` may be empty, so a
    role can be prepared before it grants anything. Errors: 400 `invalid_role_name`,
    `invalid_role_description` or `invalid_request` (also an unknown permission key); 409
    `role_name_taken`.
  - `PATCH /api/roles/{id}` with `{"name", "description", "permissions", "version"}` changes the
    given parts; a part that is left out stays as it is, and one of `name`, `description` and
    `permissions` is required (400 `invalid_request`). `version` is optional: with a stale one the
    answer is 409 `concurrent_update`. It answers 200 with the `RoleDto`, the 400 and 404 errors of
    the above, or 409 `built_in_role` or `role_name_taken`.
  - `DELETE /api/roles/{id}` answers 204, 404, or 409 `built_in_role` or `role_in_use`
    (`params.userCount`).
  - The UI's **Admin → Roles** page (`/admin/roles`) uses these endpoints.
  - **Names** are trimmed and have 2 to 40 characters: ASCII letters, digits, space, `_` and `-`.
    They are unique ignoring case, so a custom `admin` cannot sit next to `ADMIN`. A description has
    at most 200 characters.
  - **Built-in roles**: ADMIN, ANALYST, VIEWER and PUBLISHER keep their name and description and
    cannot be deleted (409 `built_in_role`, `params.change`). The permissions of ANALYST, VIEWER
    and PUBLISHER can change; ADMIN's cannot, so the platform always has a role with every permission. Sending
    the current name or description of a built-in role is not a change.
  - **An assigned role cannot be deleted**: while any user, also a disabled one, holds it, the
    answer is 409 `role_in_use`. An admin removes the role from the users first.
  - **A permission change applies to the next request** of every logged-in holder of the role,
    without a new login. An open event stream is not a request: it keeps running until it ends or
    its session ends, even when the role lost `analysis:read`.
- The platform's analysis defaults are read and saved through `/api/settings/analysis-defaults`
  ([#198](https://github.com/ramazangirgin/trading-analysis-platform/issues/198)). An
  `AnalysisDefaultsDto` has `llmProvider`, `deepThinkLlm`, `quickThinkLlm`, `maxDebateRounds`,
  `maxRiskDiscussRounds` and `version`.
  - `GET` answers the saved values. While nothing is saved it answers the catalog's defaults (upstream's,
    with its `TRADINGAGENTS_*` overrides) with one round each and `version` `null`, so a fresh install
    works without an admin step; a `GET` asks the catalog only for this fallback, so only then can it
    answer 503 `catalog_unavailable`.
  - `PUT` with the five values and `version` saves them and answers 200 with the `AnalysisDefaultsDto`
    and its new version. `version` is the one the client read, `null` only for the first save. The
    provider must be in the catalog, both models must be listed for it, and both round counts must be
    1 to 10, or the answer is 400 `invalid_analysis_defaults` with `params.field` naming the value.
    The values are checked against the catalog, so the save answers 503 `catalog_unavailable` while
    the catalog cannot be read. A
    stale `version`, also a `null` one while values are saved, answers 409 `concurrent_update`: of two
    admins who save at the same time, also the first time, the second one gets it.
- `POST /api/analyses` decides the provider, both models and both round counts itself: the five
  fields are optional and default to the platform's analysis defaults. Sending a value that differs
  from them needs `analysis:override-models`; without it the answer is 403
  `analysis_override_forbidden` with `params.field` naming the first differing field. An override
  must be in the catalog (provider, and each model for it unless the provider allows custom ids), or
  the answer is 400 `invalid_spec`. A rerun keeps the original analysis's values.
- Every other `/api/**` route needs a permission: 401 (`unauthorized`) without a session, 403
  (`forbidden`) without the permission. The permission is the `@RequiresPermission` on the endpoint's
  delegate method. The build fails (Checkstyle rule `TAP-A1`) while a delegate method has neither
  `@RequiresPermission` nor `@NoPermissionRequired("reason")`, the marker of the auth routes, and
  `EndpointPermissionsIntegrationTest` checks it at runtime. The static UI,
  `/actuator/**`, `/api/openapi/**` and `GET /api/public/**` stay open. The permissions per endpoint group:

  | Endpoints | Permission |
  |---|---|
  | `POST /api/auth/password` | a session only (everyone changes their own password) |
  | `POST /api/analyses`, `…/{id}/stop`, `…/{id}/rerun`, `POST /api/reports/rescan` | `analysis:run` |
  | `GET /api/analyses`, `…/{id}`, `…/report`, `…/prices`, `…/logs`, `…/events` | `analysis:read`; another user's analysis answers 404 without `analysis:read:all`, unless it is public or partial (not its `…/logs`) |
  | `PUT /api/analyses/{id}/visibility` | `analysis:read`; making it public or partial needs `analysis:share` too, checked by the use case |
  | `GET /api/catalog`, `GET /api/health` | `analysis:run` or `settings:read` |
  | `GET /api/secrets` | `settings:read` |
  | `PUT` and `DELETE /api/secrets/{name}` | `settings:keys:write` |
  | `GET /api/settings/analysis-defaults` | `analysis:run` or `analysis:defaults:manage` |
  | `PUT /api/settings/analysis-defaults` | `analysis:defaults:manage` |
  | `GET /api/presets` | `preset:read` |
  | `POST`, `PUT` and `DELETE /api/presets…` | `preset:manage` |
  | `GET /api/users`, `GET /api/users/{id}` | `user:read` |
  | `GET /api/roles`, `GET /api/roles/{id}` | `user:read` or `role:manage` |
  | `POST`, `PATCH` and `DELETE /api/roles…` | `role:manage` |
  | `POST` and `PATCH /api/users…` (create, update, `…/password-reset`, `…/unlock`) | `user:manage` |

  Three roles are built in. ADMIN has every permission, and the first admin has it. ANALYST runs
  analyses, reads and deletes analyses, manages presets and reads the settings. VIEWER reads
  analyses and presets. `analysis:defaults:manage` (save the platform's analysis defaults) and
  `analysis:override-models` (choose provider, models and rounds per analysis) are held by ADMIN
  only; ANALYST and VIEWER do not have them, and a custom role can be given them. The fourth
  built-in role, PUBLISHER, holds only `analysis:share` (make one's own completed analyses public),
  which ADMIN holds too; an admin lets a user publish by adding PUBLISHER to their roles, which
  applies at the user's next login. Analyses are the user's own: the user who started a run owns it (a rerun
  belongs to whoever reran it, imported runs to the first admin), and another user's analysis
  answers 404 like a missing one, unless the user has `analysis:read:all` (ADMIN) or the analysis is
  public. A public analysis is readable (`GET /api/analyses/{id}`, `…/report`, `…/prices`,
  `…/events`) with `analysis:read`, but its log, stop and rerun answer 404 for everyone but its
  owner and `analysis:read:all`. Presets are the user's own too: the user who saved one owns it,
  and another user's preset answers 404 like a missing one, unless the user has `preset:read:all`
  (ADMIN). `GET /api/presets?allUsers=true` lists everyone's presets with that permission and the
  own ones without it. A VIEWER cannot save presets, so has none. On the first start after the
  upgrade, analyses and presets without an owner are given to the first
  admin. `GET /api/analyses?scope=MINE|SHARED|ALL` (default `MINE`) lists the caller's own
  analyses, the public analyses of other users, or, with `analysis:read:all`, everyone's; `ALL`
  without that permission lists the own ones. Admins can add custom roles with any set of
  permissions. The permissions of ANALYST, VIEWER and PUBLISHER can be changed, ADMIN's cannot, and
  no built-in role can be renamed or deleted.
- `PUT /api/analyses/{id}/visibility` with `{"visibility": "PUBLIC" | "PARTIAL" | "PRIVATE",
  "version"}` makes an analysis public, partial or private (the `AnalysisDto` has `visibility`,
  `visibilityChangedAt`, `version`, and `canManage`, `canChangeVisibility` and
  `allowedVisibilities`, the values the caller may change it to). Only the owner makes an analysis
  public or partial, and only a `COMPLETED` one, with `analysis:share`. The owner makes it private
  again at any time, also without the permission; an admin with `analysis:share` makes any public
  or partial analysis private, and a partial one public, but never makes another user's analysis
  partial (404). A partial analysis is readable by every user with `analysis:read` like a public
  one. Answers: 200 with the `AnalysisDto`; 403 `analysis_share_forbidden` (the owner
  lacks `analysis:share`); 404 (not visible to the caller, or someone else's analysis made public);
  409 `analysis_not_shareable` (`params.status`, the analysis is not completed); 409
  `concurrent_update` (a stale `version`). Asking for the visibility the analysis has changes
  nothing. The UI hides the links, buttons and pages the user has no permission for; the backend
  rules are what protect.
- The live event stream of a run (`GET /api/analyses/{id}/events`) needs a session and
  `analysis:read` like the other analysis reads (another user's run answers 404 unless the user has
  `analysis:read:all` or the analysis is public), and a stream that is open when its session ends (logout, idle timeout,
  a password change in another session, a disable or password reset by an admin) is closed by the server.
- Every unsafe request needs the CSRF token: the SPA echoes the `XSRF-TOKEN` cookie in the
  `X-XSRF-TOKEN` header, or gets 403 `invalid_csrf_token`. `GET /api/auth/me` sets the cookie.
- Sessions live in the server's memory, so a restart logs everyone out. The idle timeout is
  `PLATFORM_SESSION_TIMEOUT` (default `30m`).

### Tasks

```sh
mise run setup         # uv sync for ta-runner (downloads TradingAgents), frontend packages, Git hooks, a container engine if there is none
mise run container-engine  # check Docker or Podman, and install one when there is none (scripts/container-engine.sh)
mise run dev           # backend on :8080 + Vite with hot reload on http://localhost:5173 (starts the local PostgreSQL)
mise run build         # all tests (ArchUnit included), lint (Checkstyle included), dependency checks (Gradle analysis, Knip), and the single jar
mise run run           # the single jar on http://127.0.0.1:8080, rebuilt when something changed (starts the local PostgreSQL)
mise run db            # start the local PostgreSQL container (run and dev do it for you)
mise run db:stop       # stop it; the data stays in the volume
mise run db:reset      # remove the container and its volume: the next start is an empty database
mise run test          # backend + frontend + ta-runner tests
mise run runner-test   # ta-runner lint, import contracts, dependency check (deptry) and tests only
mise run e2e           # end-to-end tests: the jar on a throwaway PostgreSQL container, in Google Chrome with login on (a seeded user), ta-runner replaying a recording
mise run screenshots   # retake the README's screenshots (e2e/screenshots/*.shot.ts), the same way
mise run check         # quick check before pushing: package structure, every domain with persistence has its conventions test, lint, formatting, type-check, dependency declarations (Gradle analysis, Knip, deptry), the agent scripts' and skill helpers' Python tests (`agent:test`, `skill:test`; no other tests)
mise run format        # format every Java (Spotless), frontend and e2e (Prettier) file
mise run format-check  # check the formatting of every Java, frontend and e2e file, as CI does
mise run hooks         # install the Git hooks (lefthook.yml)
mise run api-types     # refresh the frontend's API types from the running backend
mise run docker-build  # the platform and ta-runner Docker images
mise run server:deploy # set up the server in deploy/server/inventory.ini over SSH and deploy the Compose stack on it (Ansible)
mise run server:tunnel # open the deployed UI from this machine: an SSH tunnel, then http://127.0.0.1:8080 (with a domain, https://<domain> works too)
mise run server:check  # ansible-lint and the playbook's syntax, without a server (not part of check)
mise run server:test-setup  # the server setup scripts against a throwaway Ubuntu container (needs Docker or Podman)
mise run server:test   # the whole server deployment with THIS machine as the server: throwaway machines only (CI)
mise run ci:test-changes  # the tests of scripts/ci-changes.sh, which decides the CI jobs a change needs
mise run version       # the platform version
mise run version:bump minor   # raise it (major, minor or patch) in every file; see Versioning and releases
```

`run`, `dev`, `build`, `test`, `e2e` and `screenshots` need Docker or Podman (a PostgreSQL container;
the backend tests start theirs through Testcontainers; on a Podman machine the build points
Testcontainers' cleanup container at the machine's own socket, so no local setting is needed). `mise tasks` lists them. Tasks run with the pinned Java on `PATH` and `JAVA_HOME` set, so nothing
needs overriding; with [mise activated](https://mise.jdx.dev/getting-started.html#activate-mise) in
your shell, plain `./gradlew` and `uv` in this directory use the same versions.

### Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to `main`, on every pull
request, and on demand (*Run workflow* on the Actions tab). A branch without a pull request has no
CI until one is opened.

| Job | What |
|---|---|
| Changes | [`scripts/ci-changes.sh`](scripts/ci-changes.sh) lists the files the change touches (against the merge base with the pull request's base branch) and decides which jobs run; its test is `mise run ci:test-changes`. A change that only touches `docs/`, `.plans/`, `README.md`, `CLAUDE.md`, `.claude/` or `scripts/agent/` skips the build, jar, end-to-end, image and runner-image jobs (and the ta-runner job unless it touches `scripts/agent/` or `.claude/skills/`); *Server deploy (Ansible)* runs only when `deploy/`, `mise.toml` or `.github/` changed; *Server deploy end-to-end* only for the files listed in its row. Any file not on that list counts as an application change. On demand and on `main`, everything runs: the release takes the jar of the run on `main` |
| Backend and frontend | One Gradle run, `./gradlew spotlessCheck :frontend:pnpmLint build`: the formatting of every Java and frontend file first (a formatting failure stops the build), then the build: Spotless, Checkstyle, every Gradle test (ArchUnit, the Docker runner against the runner's own Docker, every repository and Spring Boot test against PostgreSQL in a container (Testcontainers), the database naming and schema checks of every domain (uppercase names, explicit constraint and index names, everything in the domain's own schema, a conventions test in every domain with persistence), the custom Checkstyle checks with their 100% coverage gate and the project rules' fixtures), the dependency analysis (every module declares the libraries it uses and no others), frontend lint, tests and Knip (unused and unlisted packages) |
| Platform jar | `./gradlew :backend:bootJar` in a job of its own, so the platform image and the end-to-end tests start while the build job still runs its tests. A red build still fails **CI passed**, so that jar is never released |
| ta-runner | `mise run runner-test`: ruff, import-linter (package structure), deptry (declared and imported packages match) and pytest, upstream contract tests included; then `mise run agent:test`: the Python tests of the agent scripts ([`scripts/agent/`](scripts/agent)); then `mise run skill:test`: the Python tests of the Claude Code skills' helpers ([`.claude/skills/`](.claude/skills)) |
| Version | The version is the same in every file; in a pull request into `main`, it is also higher than `main`'s and than the latest release tag, and not yet tagged (Renovate's update pull requests are exempt from the bump) |
| ta-runner image | Built beside the build job, then run under the platform's lockdown flags; the image that passed is handed to the platform image job |
| ta-runner image (arm64) | The same image built on an arm64 runner (`ubuntu-24.04-arm`) and run under the same lockdown flags, so a dependency without arm64 wheels shows up before anyone deploys to an arm64 server |
| Server deploy (Ansible) | `mise run server:check` without a server: `ansible-lint` (production profile) and the playbook's syntax; then `mise run server:test-setup`: the setup scripts against a throwaway Ubuntu 24.04 container ([`deploy/server/`](deploy/server/README.md#testing)) |
| Server deploy end-to-end | Only when `deploy/`, the ta-runner Dockerfile, the CI workflow or `scripts/ci-changes.sh` changed (otherwise it is skipped): `mise run server:test`, with the runner as the server. The setup scripts, a deploy over SSH to `127.0.0.1`, a second deploy with `changed=0`, the UI through the SSH tunnel, only SSH open, a backup, a deploy of another commit that rebuilds, key-only SSH |
| End-to-end tests | The e2e formatting (Prettier), then the platform jar job's jar, on a throwaway PostgreSQL container, in Google Chrome, in parallel (3 workers; [`e2e/`](e2e/), Playwright), with login on as the first admin: login, wrong credentials, redirect back, logout and the back button; change password (menu, wrong current password, weak new password); user management (create a user, first login with the one-time password, disable); new analysis, live run page and decision; reports and Markdown export; comparing two runs; settings (keys masked, presets). `ta-runner` replays a recording (`TA_RUNNER_REPLAY`, [`artifact/ta-runner/tests/fixtures/replay-run`](artifact/ta-runner/tests/fixtures/replay-run)) instead of calling an LLM. Traces are uploaded when a test fails |
| Platform image and Compose smoke test | The platform image (GitHub's build cache) and the ta-runner image from the job above, then [`deploy/smoke-test.sh`](deploy/smoke-test.sh): the Compose stack comes up, an analysis runs in its own container, and the data survives a database restart and `down`/`up` |

A last job, **CI passed**, succeeds only if every job succeeded or was skipped; a failed or cancelled
job fails it. New jobs go into its `needs` list, so
the rules on `main` never have to change. Those rules (the repository ruleset "main"):

- Changes reach `main` only through pull requests; no direct or force pushes, and `main` cannot be
  deleted.
- A pull request merges once **CI passed** is green, its branch is up to date with `main`, and a
  code owner has approved it ([`.github/CODEOWNERS`](.github/CODEOWNERS): the admins).
- Admins can merge their own pull requests without an approval, and can merge one whose checks
  failed (GitHub's "bypass" option on the pull request). Nothing merges on its own: auto-merge is off.

Tools come from `mise.toml`, as locally. A newer push to the same pull request cancels the run in progress
(not on main). The smoke test also runs locally: `deploy/smoke-test.sh /absolute/path/for/data` after
`mise run docker-build`.

#### Dependency updates

The hosted [Renovate](https://docs.renovatebot.com/) app ([`.github/renovate.json5`](.github/renovate.json5))
keeps every pinned dependency current: the Gradle version catalog and wrapper, the pnpm packages of
`artifact/frontend/` and `e2e/`, Node.js and pnpm of the build, ta-runner's uv dependencies, the
TradingAgents release, `mise.toml`'s tools, the Dockerfiles, the Compose file and the workflows'
actions. On Monday mornings (Europe/Berlin) it opens **one pull request with every update**, majors
included ("Update all dependencies", branch `renovate/all`), plus one for lock file maintenance. It
only takes releases at least **14 days old**; younger ones wait as "pending" on the dashboard. Java
stays on 25, Python on 3.12, Node.js on LTS majors, and PostgreSQL gets no major updates. Renovate's
dependency dashboard issue lists everything it tracks. Nothing merges automatically: the update pull
request runs the full CI and is merged by an admin like any other. Update pull requests skip the
version bump and ship with the next release (see below).

The schedule also holds for a manual run: to get the update pull request before Monday, tick
"Create all awaiting schedule PRs at once" on the dashboard issue (that starts a run). The *Renovate
run* workflow ([`renovate-run.yml`](.github/workflows/renovate-run.yml)), from the Actions tab or
`gh workflow run renovate-run.yml`, ticks the dashboard's "run again" checkbox: a run without the
schedule, for example to rebase the pull request after `main` moved.

When the update includes TradingAgents, CI fails until you finish it on the pull request's branch:
run `uv lock` in `artifact/ta-runner/` and commit `uv.lock`, and adapt `ta_runner/engine/compat.py`
and the contract tests to the new release.

The Claude Code skill `renovate-update`
([`.claude/skills/renovate-update/SKILL.md`](.claude/skills/renovate-update/SKILL.md)) does all of
this from your machine: `/renovate-update` ticks every mature update on the dashboard (never the
pending ones) and requests a run, waits for the pull requests and their CI, and fixes a red CI on the
update branch: it adapts the code to the new release and moves the tools around it along. When that
is not possible yet, it shows you why and asks whether to hold the dependency back (an
`allowedVersions` rule) and open a follow-up issue, which the rule links. It stops at a green pull
request and merges only when you say so. The fixing is a skill of its own, `renovate-fix`
([`.claude/skills/renovate-fix/SKILL.md`](.claude/skills/renovate-fix/SKILL.md)), which
`renovate-update` uses: `/renovate-fix` takes every open Renovate pull request one by one
(`renovate/all` first), and `/renovate-fix <pr>` only the given ones, without a new Renovate run.

Renovate only moves direct and pinned dependencies, so Dependabot alerts on transitive ones stay
open. The Claude Code skill `dependabot-fix`
([`.claude/skills/dependabot-fix/SKILL.md`](.claude/skills/dependabot-fix/SKILL.md)) closes them:
`/dependabot-fix` takes all open alerts, `/dependabot-fix maven` (or `pip`, `npm`) one ecosystem, and
`/dependabot-fix 16 17 18` the given alert numbers. It groups the alerts by release family, finds
where each group comes from, picks the newest mature (at least 14 days old), stable version that fixes
every alert of the group and checks it against `renovate.json5`. After you approve the table it pins
the version on a branch, one commit per group, with a comment that names the advisories and says when
the pin can go (the parent release that brings the fix), and opens a pull request that lists the
alerts it closes. Groups that only a pre-release or a major fixes are reported, not applied. For
Gradle, GitHub's alerts come from Automatic Dependency Submission, which resolves **every**
configuration of every project (test fixtures and plugin classpaths included) on each push to
`main`; the skill checks the fix the same way (`alerts.py gradle-scan`) before it commits. The alerts
close once that submission has run on `main` after the merge, and `alerts.py after-merge` then
reports each one as fixed or says why it is still open. The helper's tests run with
`mise run skill:test`.

### Versioning and releases

The whole repository has one [Semantic Versioning](https://semver.org/) version, `MAJOR.MINOR.PATCH`:
the backend, the frontend and ta-runner always report the same one. It is set in
[`gradle.properties`](gradle.properties); `artifact/frontend/package.json` and
`artifact/ta-runner/ta_runner/__init__.py` carry copies, which `mise run version:bump` keeps in step
and CI checks. The running app shows it at the bottom of every page and at `/actuator/info`; the
images carry it as a tag and as the `org.opencontainers.image.version` label.

Every pull request into `main` raises the version in its own diff, or the **Version** job fails:

```sh
mise run version:bump minor   # a new feature or any other change
mise run version:bump major   # a breaking change: the REST API, the database without a migration,
                              # configuration, the ta-runner contract or the CLI
mise run version:bump patch   # hotfixes only
```

Renovate's dependency update pull requests are the one exception: they skip the bump (the hosted
app cannot run the script) and ship with the next pull request that raises the version.

The Claude Code skill `pr-ready` ([`.claude/skills/pr-ready/SKILL.md`](.claude/skills/pr-ready/SKILL.md))
takes a branch to a pull request ready for review: `/pr-ready` (the current branch) or
`/pr-ready <pr>` raises the version when the branch does not, runs `mise run check`, pushes, opens
or updates the pull request, brings it up to date with `main` and fixes a red CI until **CI passed**
is green. It merges only when you say so. `dependabot-fix`, `renovate-fix` and `develop-issue` use it
for their pull requests.

A branch that fell behind `main` with conflicts is brought up to date by the Claude Code skill
`merge-main-into-branch` ([`.claude/skills/merge-main-into-branch/SKILL.md`](.claude/skills/merge-main-into-branch/SKILL.md)):
`/merge-main-into-branch` merges `origin/main` into the current branch (no rebase, no force push),
commits the merge as it is, conflict markers included, and resolves the conflicts in a second
commit of its own, from the branch's commits, `main`'s commits on the same lines and the branch's
plan; the reviewer sees each resolution with `git show`. It bumps the version again when `main`
caught up, and stops and asks when a conflict cannot be resolved with confidence. `develop-issue`
uses it to update its pull requests. A branch with such a merge commit is merged with
`gh pr merge <pr> --merge`: GitHub cannot rebase-merge it.

Every version raised on `main` is a release. After **CI passed** on `main`,
[`.github/workflows/release.yml`](.github/workflows/release.yml) tags the commit `vX.Y.Z` and
publishes a [GitHub Release](https://github.com/ramazangirgin/trading-analysis-platform/releases)
with notes generated from the merged pull requests and the jar with its SHA-256 checksum. A new
latest release is then deployed to analystock.app by
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) once the reviewer approves it
([automatic deploys](deploy/server/README.md#automatic-deploys-on-release)). Tags are
created by that workflow only, never by hand. Two pull requests that raise to the same version cannot
both merge: the second one must be updated with `main` and bumped again. The rules and why:
[versioning and releases](docs/coding-convention/repository-versioning-and-releases.md).

### Agentic development
=======
## Agentic development
>>>>>>> origin/main

An issue can be taken to a reviewed pull request by agents, with you deciding at two points:
approving the plan and merging the pull request. Everything runs on your machine with Claude Code
and your own Claude Code login: Sonnet as the developer agent, Opus as the review agent. Agents
never merge. Each step, what to check at each decision, how to stop, continue or redo a run, and
the settings: [docs/agentic-development.md](docs/agentic-development.md).

## License

Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Ramazan Girgin.

[TradingAgents](https://github.com/TauricResearch/TradingAgents), which `ta-runner` installs, is
also Apache-2.0-licensed and remains under its authors' copyright.
