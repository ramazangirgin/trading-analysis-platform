# Plan: The server decides an analysis's provider, models and rounds; the UI shows them

- **Issue**: #198 (Admin-defined provider, models and debate rounds for analyses; only permitted users override them per analysis)
- **Plan**: 2 of 2 for this issue; depends on: 198-analysis-defaults (merged first)
- **Version bump**: minor (a new feature; the start request's five model/round fields become optional)

## Goal

Every analysis uses the platform's saved provider, deep- and quick-thinking models and debate round
counts, decided by the server — the client can no longer choose them. A user with
`analysis:override-models` may change them per analysis; everyone else gets the platform values and
a start request that sends different ones is refused with 403. The New analysis page shows the five
values read-only for everyone else and editable-from-defaults for those who may override; the
Settings page gets an Analysis defaults card for admins. A rerun keeps the values of the original
analysis.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend orchestration + analysis domain | backend-java-package-structure.md (the new use case spans settings and analysis, so it lives in `orchestration`, one sub-package per feature); the 403's error code joins `AnalysisError`, next to its exception |
| Backend BFF | backend-java-rest-api.md (errors are exceptions mapped in the handlers, never caught and mapped in a delegate; a delegate may still raise a web error for a web-only case); `@RequiresPermission` stays on the delegate method; the in-method permission check follows the `ANALYSIS_READ_ALL` pattern (`currentUser.has(...)`) |
| Frontend | frontend-folder-structure.md (`features/settings` api and components, `features/analysis`, page composition in `pages/`, i18n in `shared/i18n/locales/`) |
| e2e | the existing Playwright setup (`e2e/tests/*.e2e.ts`, admin session from the global setup, created users with the `users.e2e.ts` pattern, `TA_RUNNER_REPLAY`) |
| Docs | README sections 4 and 5; screenshots taken with `mise run screenshots` (`e2e/screenshots/*.shot.ts`, 1440×900, dark theme) |

## Design

### Starting an analysis: the server decides

- New orchestration feature `orchestration.analysisstart`:
  - model `StartAnalysisRequestModel`: the start parameters with the five model/round fields
    nullable (`null` = the client did not send the field).
  - inbound `StartAnalysisUseCase`: `Analysis start(StartAnalysisRequestModel request, OwnerId
    owner, boolean mayOverrideModels)`, implemented by a package-private service:
    1. effective values = `ManageAnalysisDefaultsUseCase.get()` (plan 1's orchestration feature).
    2. If any of the five fields is non-null and differs from the platform value and
       `mayOverrideModels` is false → throw
       `AnalysisException(AnalysisError.OVERRIDE_FORBIDDEN, ..., Map.of("field", <the field>))`.
       Sending nothing, or exactly the platform values, is accepted.
    3. Build the final `AnalysisSpec`: each of the five is the request's field when it was sent
       (permitted case), else the platform value; the rest as today's mapper defaults
       (assetType `STOCK`, outputLanguage `English`, checkpointEnabled `false`).
    4. Delegate to the analysis domain's `StartAnalysisUseCase.start(spec, owner)`, which validates
       the final spec (`AnalysisSpecValidator`: formats and rounds 1..10) and stores it as today.
  - The five values are stored in the spec as they always were, so the run detail, compare view and
    report show the values actually used. `RerunAnalysisUseCase` reuses the stored spec of the
    original analysis, so a rerun reproduces the original run with its original values — whoever
    reruns it and whatever the platform values are meanwhile. This answers the issue's open
    question: rerun is unchanged, it never re-resolves the platform values.
  - A `CatalogUnavailableException` from step 1 surfaces through the existing 503 mapping.
- `AnalysisError` gains `OVERRIDE_FORBIDDEN`; `AnalysisExceptionToApiExceptionMapper` maps it to
  403 (error code `analysis_override_forbidden`).
- BFF:
  - `StartAnalysisRequest`: `llmProvider`, `deepThinkLlm`, `quickThinkLlm` lose `@NotBlank` (all
    five model/round fields optional; the other fields unchanged).
  - `StartAnalysisRequestToAnalysisSpecMapper` no longer fills `maxDebateRounds = 1` /
    `maxRiskDiscussRounds = 1`; it maps the request to `StartAnalysisRequestModel` (DTO enums →
    domain enums).
  - `AnalysesApiDelegateImpl.startAnalysis`: computes
    `mayOverrideModels = currentUser.has(Permission.ANALYSIS_OVERRIDE_MODELS)` and calls the
    orchestration use case. `@RequiresPermission(ANALYSIS_RUN)` stays.
- Override validation: the final spec is checked by `AnalysisSpecValidator` as today. Whether an
  override must also match the catalog is an open question (see below); the plan's answer is in
  "Open questions" until it is resolved, and the integration tests and README wording follow it.

### Frontend

- `features/settings`:
  - `api.ts`: `getAnalysisDefaults()` and `updateAnalysisDefaults(request)`; the `AnalysisDefaults`
    type in `shared/api/types.ts` (from the regenerated `schema.d.ts`).
  - `components/AnalysisDefaultsCard.vue`, shown on `SettingsPage.vue` between the API keys and the
    presets, only with `session.can(PERMISSIONS.analysisDefaultsManage)`: provider select (catalog
    providers), deep- and quick-model selects filtered by the chosen provider (the New analysis
    page's filtering), both round inputs (1..10), Save. It loads the current values on mount
    (`getAnalysisDefaults`), keeps the `version`, PUTs on save; a 409 `concurrent_update` shows the
    message and reloads (the presets rename-conflict pattern); a `catalog_unavailable` answer shows
    the existing hint.
- New analysis page (`NewAnalysisPage.vue`):
  - on mount, load the platform defaults (`getAnalysisDefaults`) together with the catalog; the
    five fields start from them (they replace the current `catalog.defaults` application).
  - `overrideModels = session.can(PERMISSIONS.analysisOverrideModels)`:
    - without: the five fields render read-only with the platform values and a "Set by the
      administrator" hint, and are not sent in the start request;
    - with: the fields are editable from the platform values, a "Reset to defaults" link restores
      them, and the start request sends only the fields that differ from the platform values (the
      others are omitted).
  - Presets: with the permission, saving and applying keep the five values (as today); without it,
    applying a preset ignores the preset's five values (the platform values stay shown) and saving
    a preset does not store them (`PRESET_FIELDS` minus the five).
  - The changed-fields computation and the preset-field filtering go to a pure model function
    (`features/analysis/model/...`) with a Vitest spec.
- i18n (en and tr): `settings.analysisDefaults.*` (title, provider, deep model, quick model,
  debate rounds, risk rounds, save, saved), `form.defaultsHint` ("Set by the administrator"),
  `form.resetToDefaults`, `errors.analysis_override_forbidden`.

### e2e

- `settings.e2e.ts`: the admin saves the five values on the Settings card (toast, reload shows
  them); an ANALYST does not see the card.
- New `analysis-defaults.e2e.ts` (the existing helpers: created users, `logIn`, replayed runner):
  - the admin saves defaults; a created ANALYST opens New analysis and sees the five values
    read-only; starting the analysis stores exactly these values (visible on the run page);
  - the ANALYST's API request sending a different provider → 403 `analysis_override_forbidden`;
  - the admin changes one field for one analysis; the run uses it and the platform values stay
    unchanged;
  - applying a preset as the ANALYST does not change the five values.
- Screenshots (`e2e/screenshots/`): retake `settings` (the page now includes the Analysis defaults
  card) and `new-analysis` (admin, editable from defaults); add a new shot
  `new-analysis-readonly` that logs in as a created ANALYST user (the `users.e2e.ts` / `auth.e2e.ts`
  `logIn` pattern) and shows the read-only fields with the "Set by the administrator" hint.

### Docs

- README "4. Add an API key" / the Settings description: a paragraph on the Analysis defaults card
  (permission `analysis:defaults:manage`, the five values, the 409 on a stale save, the catalog
  fallback until an admin saves once).
- README "5. Start an analysis": the provider/models/rounds rows of the field table now describe
  the platform values, the read-only view without `analysis:override-models`, the
  editable-from-defaults view with it, "Reset to defaults", and the preset behaviour without the
  permission; the "you can type a model id that is not listed" sentence follows the open question's
  answer. "Run again" already says it repeats with the same settings — keep it, it now covers the
  rerun decision.
- The screenshots above.

## Work packages

### WP1: Orchestration start use case

- **Depends on**: plan 1 merged (`198-analysis-defaults`)
- **Files**: `orchestration/.../orchestration/analysisstart/model/StartAnalysisRequestModel.java`,
  `inbound/StartAnalysisUseCase.java`, `service/StartAnalysisService.java` (or similar);
  analysis core `exception/AnalysisError.java` (new code `OVERRIDE_FORBIDDEN`);
  `bff/impl/.../mapper/error/AnalysisExceptionToApiExceptionMapper.java` (403 case)
- **Steps**:
  - [ ] the model with the five nullable fields
  - [ ] the use case and its service: merge platform values and permitted overrides, raise
    `OVERRIDE_FORBIDDEN` on unpermitted differing values, build the spec, delegate to the domain
    start
  - [ ] the error code and its 403 mapping
- **Tests**: a unit test of the service with fakes: no fields sent → platform values; overrides
  with `mayOverrideModels` → merged; a differing field without it → `AnalysisException`
  `OVERRIDE_FORBIDDEN` with the field param; exactly the platform values without it → accepted;
  fields left out → platform values; the final spec still fails `AnalysisSpecValidator` cases
  (rounds outside 1..10). The existing `AnalysisServiceTest` (including rerun) stays green
  unchanged.

### WP2: BFF start endpoint

- **Depends on**: WP1
- **Files**: `bff/api/.../model/StartAnalysisRequest.java` (drop the three `@NotBlank`);
  `bff/impl/.../mapper/StartAnalysisRequestToAnalysisSpecMapper.java` (→ `StartAnalysisRequestModel`,
  no rounds defaults); `bff/impl/.../AnalysesApiDelegateImpl.java` (`mayOverrideModels` + the
  orchestration call)
- **Steps**: as above; `TestSession.validBody` for `/api/analyses` becomes the minimal body
  (ticker, tradeDate, analysts — the five left out)
- **Tests**: extend `AnalysesApiIntegrationTest` (or a new `StartAnalysisDefaultsIntegrationTest`
  next to it): an ANALYST with a minimal body → 201 and the spec carries the platform values
  (catalog fallback when nothing is saved); an ANALYST sending a differing field → 403
  `analysis_override_forbidden` with the field param; an ANALYST sending exactly the platform
  values → 201; an ADMIN sending one changed field → 201 and the spec shows the changed field and
  the platform values for the rest; a rerun by an ANALYST reproduces the original values even after
  the platform defaults changed. Adjust the existing tests that send the five fields: they either
  send the platform values, drop the fields, or run as ADMIN; `reportsInvalidInputWithAnErrorCodeAndField`
  loses the "missing model → 400" assertion (the fields are optional now) and keeps the format
  cases.

### WP3: Frontend — Analysis defaults card

- **Depends on**: WP2 (the endpoint and the generated types from plan 1)
- **Files**: `features/settings/api.ts`, `components/AnalysisDefaultsCard.vue`,
  `pages/SettingsPage.vue`, `shared/api/types.ts` (`AnalysisDefaults`), i18n locales
  (`settings.analysisDefaults.*`)
- **Steps**: API methods and type; the card (load, save with version, 409 reload, provider-filtered
  model lists from the catalog store); mount it behind `analysisDefaultsManage`; i18n texts
- **Tests**: the pure model logic spec if the card gets any (e.g. field filtering); existing
  frontend tests keep passing.

### WP4: Frontend — New analysis page

- **Depends on**: WP2, WP3
- **Files**: `pages/NewAnalysisPage.vue`, `features/analysis/model/...` (changed-fields and
  preset-field logic + its spec), i18n locales (`form.defaultsHint`, `form.resetToDefaults`,
  `errors.analysis_override_forbidden`)
- **Steps**: load the platform defaults on mount; the read-only versus editable rendering; "Reset
  to defaults"; send only the changed fields; preset apply/save without the five for non-overriders
- **Tests**: the Vitest spec of the pure logic (which fields are sent, which preset fields apply);
  the existing specs keep passing.

### WP5: e2e, screenshots, README

- **Depends on**: WP3, WP4
- **Files**: `e2e/tests/settings.e2e.ts`, `e2e/tests/analysis-defaults.e2e.ts` (new),
  `e2e/screenshots/pages.shot.ts` (+ the new shot), `README.md`
- **Steps**: the e2e scenarios and the three screenshots above; the README updates
- **Tests**: the e2e tests themselves (`mise run e2e`); the screenshots run with
  `mise run screenshots`.

## Tests

WP1's unit tests prove the server-side decision; WP2's integration tests prove the issue's 403 and
the stored values over HTTP; WP3/WP4's specs cover the frontend logic; WP5's e2e tests prove the
issue's acceptance criteria end to end (admin saves → an ANALYST's next run uses exactly these
values, visible on the run detail; the read-only page; the 403; the per-analysis override; the
preset behaviour; a fresh install's fallback is covered by plan 1).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | README.md "4. Add an API key" (Settings description) | the Analysis defaults card paragraph |
| Text | README.md "5. Start an analysis" | the five fields come from the platform settings; read-only without `analysis:override-models`, editable with it + "Reset to defaults"; preset behaviour; rerun keeps the original values |
| Screenshot | `docs/screenshots/settings.png`, retaken | the Settings page with the Analysis defaults card (admin session) |
| Screenshot | `docs/screenshots/new-analysis.png`, retaken | the form, admin view, fields starting from the platform values |
| Screenshot | `docs/screenshots/new-analysis-readonly.png`, new | the form, ANALYST view: the five fields read-only with the "Set by the administrator" hint |

## Out of scope

- The permissions, the stored `AnalysisDefaults`, the `GET/PUT /api/settings/analysis-defaults`
  endpoints and the README endpoint table: plan 1 of this issue.
- Per-user or per-role defaults, limiting which providers or models an overriding user may pick,
  cost estimates (#37).

## Open questions

- **Override validation against the catalog.** The issue's design says overrides are "validated
  against the catalog as today", but today the start flow has no catalog check (only
  `AnalysisSpecValidator`'s formats), while the acceptance criteria say "a provider or model not in
  the catalog is refused ... in an override". Recommended answer: check overrides against the
  catalog the way the UI already behaves — the provider must exist in the catalog, and each model
  must be listed for it or the provider must allow custom model ids (`customModelAllowed`, which
  keeps today's "you can type a model id that is not listed"); rounds stay 1..10. The strict
  reading (models must always be listed) would end free-form model ids for overriders.
