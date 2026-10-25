# Plan: Landing page and preview page for visitors

- **Issue**: #194 (Partially public analyses on the main page: a preview for visitors, the full analysis after sign-up)
- **Plan**: 2 of 2 for this issue; depends on: `194-preview-backend` (the `PARTIAL` visibility and the
  public endpoints)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): new pages, no
  breaking change

## Goal

A visitor who opens the main page without a login sees a **landing page** instead of the login form:
what the platform does, a **Log in** button, and cards of the latest partially public analyses with
most of each one faded and locked. A card opens the **preview page**: the allowed parts in full, the
other sections as blurred placeholders with their titles, and a call to action. After logging in, the
visitor lands on the full analysis they were looking at. The owner of an analysis has a third option,
**Partial**, in the visibility control. A logged-in user opening `/` sees the dashboard, as today.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Frontend | `frontend-folder-structure.md`: a new feature folder `features/preview/` (`api.ts` is the only caller of the HTTP client, `model/` pure logic with specs, `components/`, `index.ts` the only file others import); a page per route in `pages/`, the routes in `app/router/index.ts`; translations in `shared/i18n/locales/en.json` and `tr.json`; API types from `shared/api/types.ts`, never `schema.d.ts` directly; `eslint-plugin-boundaries` and Knip must pass |
| e2e | `e2e/tests/*.e2e.ts`: the shared admin's session must not change; users created with unique names through the API (as `sharing.e2e.ts`); `e2e/screenshots/*.shot.ts` for screenshots |
| Docs | Root `README.md`, `docs/screenshots/`, `docs/agentic-development.md` unchanged |

## Design

### Routes and the router guard

- `app/router/index.ts`:
  - `/` keeps the route name `dashboard` and gets `meta: { visitors: true }`, its component is a new
    `pages/HomePage.vue` that renders `DashboardPage` for a logged-in user and `LandingPage` for a
    visitor;
  - `/preview/:id` (`name: 'preview'`, `pages/PreviewPage.vue`) gets `meta: { public: true }` like
    `/about`.
- The guard: a `public` route passes as today; a `visitors` route passes **only without a session**, so
  with a session `/` still goes through the password and permission gates (a user who must change the
  password is still sent there). Every other route without a session still goes to `/login`.
  `router/index.spec.ts` (new) covers the three cases.
- `/preview/:id` for a logged-in user: `PreviewPage` replaces the route with `/analyses/:id` before it
  loads anything.
- The 401 handler in `main.ts` must not fire for the public calls: they answer `200` or `404`, and a
  visitor has no session to lose; the existing handler is checked, not assumed, and changed only if it
  redirects a visitor from a public page.

### The `preview` feature

- `features/preview/api.ts`: `previewApi.list(page, size)` and `previewApi.get(id)`, calling
  `/api/public/analyses`.
- `features/preview/model/`:
  - `lockedTitles.ts`: the label key of a locked section (`sentiment_report` to
    `preview.locked.sentiment_report`), with a fallback for an unknown key; pure, with a spec;
  - `loginTarget.ts`: `loginLocation(id)` returns the login route with `redirect=/analyses/<id>`; pure,
    with a spec. `safeRedirect` already accepts it.
- `features/preview/components/`:
  - `PreviewCard.vue`: ticker, date, `RatingTag`, the excerpt in a clipped box with a fade and a lock
    ("Log in to read the full analysis"), linking to `/preview/:id`;
  - `LockedSection.vue`: the title and a block of **placeholder lines drawn by CSS** with a blur. No
    text from the server is ever put behind the blur: the placeholder is static, so there is nothing to
    reveal in the page source or the developer tools;
  - `SignUpCallToAction.vue`: the headline and the buttons; takes the analysis ID for the redirect.
- `pages/LandingPage.vue` (inside `HomePage`): the headline and a short description of the platform, the
  **Log in** button at the top, the cards in a grid, **Show more** (page by page, `hasMore` from the API),
  and the empty state (headline and button only). The page's own errors use `useLabels`'s `error`.
- `pages/PreviewPage.vue`: the header (ticker, date, rating), the allowed sections through the existing
  `MarkdownView` of the reports feature (its `index.ts` export), then `LockedSection` for each locked
  title, with the call to action between the allowed part and the locked ones and at the end. A `404`
  shows "Analysis not found." with a link to the landing page (the analysis was made private, or never
  was partial).
- `App.vue`: the navigation shows no analysis links without a session; the header has the brand, the
  language switch and **Log in**. `LoginPage.vue` gets a link back to the landing page next to the
  existing About link.

### The visibility control

- `features/analysis/model/visibility.ts`: `visibilityControl(analysis, userId)` returns, for the owner or
  an admin with `allowedVisibilities`, `{ kind: 'select', options }` with the three options **Private**,
  **Public (logged-in users)**, **Partial (preview for visitors)**, each `disabled` when it is not in
  `allowedVisibilities` (with the reason `notCompleted` for an analysis that is not completed), and
  `{ kind: 'none' }` otherwise. The admin's **Make private** button is the same select, so the earlier
  `switch` and `makePrivate` kinds go.
- `RunDetailPage.vue`: the select in the header; choosing **Partial** opens a confirmation, "Anyone on the
  internet will see the preview of this analysis"; choosing **Public** keeps its confirmation; choosing
  **Private** needs none. After a change the page shows the returned analysis; `concurrent_update`
  reloads it, as today.
- `AnalysisTable.vue`: a **Partial** tag next to the **Public** one. The shared tab holds both kinds.
- `shared/api/types.ts` exports what the new components need from the generated types.

### Translations

`en.json` and `tr.json`: the landing page (headline, description, buttons, lock text, empty state, show
more), the preview page (call to action, locked titles for each section and speaker, not found), the
three visibility options and the confirmation, the Partial tag, the link on the login page. The
`locales.spec.ts` check keeps both files in step.

## Work packages

### WP1: API calls, routes, the guard and the visibility control

- **Status**: done
- **Depends on**: none (the backend plan is merged first)
- **Files**:
  - `artifact/frontend/src/features/preview/api.ts`, `index.ts`, `model/lockedTitles.ts`, `model/loginTarget.ts` and their specs (new)
  - `artifact/frontend/src/app/router/index.ts`, `router/index.spec.ts` (new)
  - `artifact/frontend/src/features/analysis/model/visibility.ts`, `visibility.spec.ts`, `components/AnalysisTable.vue`, `index.ts`
  - `artifact/frontend/src/shared/api/types.ts`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
- **Steps**:
  - [x] The preview API and the two pure models.
  - [x] The routes and the guard with the `visitors` flag.
  - [x] The select model, the Partial tag, the translations.
- **Tests**:
  - `api.spec.ts` for the preview calls (path, page and size in the query).
  - `lockedTitles.spec.ts`, `loginTarget.spec.ts`: keys, the fallback, the redirect.
  - `router/index.spec.ts`: a visitor reaches `/` and `/preview/:id` and is sent to `/login` from
    `/analyses`; a user who must change the password is sent to `/password` from `/`.
  - `visibility.spec.ts`: the owner's options for a private completed, a private running, a public and a
    partial analysis; an admin on another user's partial analysis (Public and Private enabled, Partial
    disabled); another user: `none`.

### WP2: The landing page, the preview page and the control in the detail page

- **Depends on**: WP1
- **Files**:
  - `artifact/frontend/src/features/preview/components/PreviewCard.vue`, `LockedSection.vue`, `SignUpCallToAction.vue` (new)
  - `artifact/frontend/src/pages/HomePage.vue`, `LandingPage.vue`, `PreviewPage.vue` (new), `RunDetailPage.vue`, `LoginPage.vue`
  - `artifact/frontend/src/app/App.vue`
  - `artifact/frontend/src/features/reports/index.ts` (exports `MarkdownView` if it does not yet)
- **Steps**:
  - [ ] The components and the two pages, the empty state and the 404.
  - [ ] The header for a visitor and the link on the login page.
  - [ ] The select, its confirmations and the translated error codes in `RunDetailPage.vue`.
- **Tests**:
  - Component specs: `LockedSection` renders the title and no text from its props other than the title;
    `PreviewCard` links to `/preview/:id`; `SignUpCallToAction` links to the login route with the
    `redirect` of the analysis.
  - `vue-tsc`, ESLint boundaries and Knip (`mise run check`).

### WP3: e2e, screenshots and docs

- **Depends on**: WP2
- **Files**:
  - `e2e/tests/partial.e2e.ts` (new), `e2e/tests/sharing.e2e.ts` (the control is a select now)
  - `e2e/screenshots/landing.shot.ts`, `preview.shot.ts` (new), `login.shot.ts`, `share.shot.ts`
  - `docs/screenshots/landing.png`, `preview.png` (new), `login.png`, `run-share.png` (retaken)
  - `README.md`
- **Steps**:
  - [ ] `partial.e2e.ts` (below), the changed `sharing.e2e.ts`.
  - [ ] The screenshot shots and `mise run screenshots`.
  - [ ] The README changes below.
- **Tests**: `partial.e2e.ts`: the admin creates user A with ANALYST and PUBLISHER through the API (unique
  name); A gets a completed replayed analysis and makes it **Partial** in the detail page after the
  confirmation. A visitor (a fresh context, no storage state) opens `/`, sees the landing page and A's
  card, not the login form, and the page holds no username of A. The visitor opens the card: the ticker,
  date, rating and the market report are shown, the other sections are locked titles, and the page
  source holds none of their text. The visitor clicks **Log in**, logs in as another user (created the
  same way) and lands on `/analyses/<id>`, read-only. A makes it Private: the visitor's reload of the
  preview says "Analysis not found." and the landing page shows no card.

## Tests

- Vitest for the API calls, the pure models, the router guard and the components; `vue-tsc` and ESLint
  through `mise run check`.
- e2e: `partial.e2e.ts` is the issue's e2e criterion; `sharing.e2e.ts` stays green with the select.
- The backend plan's integration tests own the acceptance criteria on hidden data, 404, 401 and 429.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "3. Log in" or a new short section before it | What a visitor sees on the main page, the preview page and the log-in step; a link back from the login page |
| Text | `README.md`, "6. Watch it and read the results" (the sharing paragraph) | The visibility control has three options; the Partial confirmation and what it means for visitors |
| Text | `README.md`, "7. Past analyses" | The **Partial** tag next to the **Public** one |
| Screenshot | `docs/screenshots/landing.png`, new (`e2e/screenshots/landing.shot.ts`) | The landing page as a visitor with two partial analyses from replayed runs; needs the runs made partial through the API (the new README section) |
| Screenshot | `docs/screenshots/preview.png`, new (`preview.shot.ts`) | The preview page of one of them: the market report in full, the locked placeholders and the call to action (same section) |
| Screenshot | `docs/screenshots/run-share.png`, retaken | The detail page as the owner with the visibility select open and the Partial confirmation showing (README "6. Watch it and read the results") |
| Screenshot | `docs/screenshots/login.png`, retaken | The login page with the new link back to the landing page (README "3. Log in") |

## Out of scope

- The backend: plan 1, `194-preview-backend`.
- A marketing site beyond the landing page, SEO and Open Graph cards, statistics on visits or sign-ups.
- Registration: see Open questions.

## Open questions

- **"Create an account" has nowhere to go.** The issue asks for a **Create an account** button and for
  landing on the full analysis "after registering a new account". Registration is #186 and is still
  open: today only an admin creates users. This plan ships **Log in** only. The call to action takes its
  buttons from one component and the redirect already works through `safeRedirect`, so #186 adds its
  button and its own e2e step there without touching the pages. The acceptance criteria "the same after
  registering a new account" and the **Create an account** button then belong to #186's pull request.
  The alternative is to wait for #186 before developing this issue. The plan assumes **Log in only now**.
- **Where the Log in call to action says what it offers.** Without registration the text says "Log in to
  read the full analysis", not "Create an account". The plan assumes this wording, with the Turkish
  translation, until #186.
