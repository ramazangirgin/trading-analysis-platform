# Login

The backend has a server-side session login ([#78](https://github.com/ramazangirgin/trading-analysis-platform/issues/78)),
always on. The first start creates the first admin ([Quick start](user-guide.md#3-log-in)). The UI has a login
page (`/login`):

![The login page](screenshots/login.png)

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

![The change-password page](screenshots/change-password.png)

Investment disclaimer: the login page included, every page shows a muted line under the header,
and the public About page (`/about`) shows the full text (see [Log in](user-guide.md#3-log-in)). The wording is
`app.disclaimer` in the translation files, shown by the `DisclaimerNotice` component.

![The About page](screenshots/about.png)

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
