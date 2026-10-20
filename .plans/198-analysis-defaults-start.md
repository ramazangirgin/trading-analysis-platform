# Plan: The server decides an analysis's provider, models and rounds; the UI shows them

- **Issue**: #198 (Admin-defined provider, models and debate rounds for analyses; only permitted users override them per analysis)
- **Plan**: 2 of 2 for this issue; depends on: 198-analysis-defaults (merged first)
- **Version bump**: minor (a new feature; the start request's five model/round fields become optional, which no client breaks on)

## Goal

Every analysis uses the platform's saved provider, deep- and quick-thinking models and debate round
counts, decided by the server: the client no longer chooses them. A user with
`analysis:override-models` may change them for one analysis; everyone else gets the platform
values, and a start request that sends different ones is refused with 403. The New analysis page
shows the five values read-only to everyone else and editable, starting from the platform values,
to those who may override; the Settings page gets an Analysis defaults card for
`analysis:defaults:manage`. A rerun keeps the values of the original analysis.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend orchestration + analysis domain | backend-java-package-structure.md (the new use case spans settings, catalog and analysis, so it lives in `orchestration`, one sub-package per feature with only `inbound`, `service`, `model`); the new error code joins `AnalysisError`, next to its exception |
| Backend BFF | backend-java-rest-api.md (errors are exceptions answered by `DomainExceptionHandler` through the domain's mapper, never caught in a delegate); `@RequiresPermission` stays on the delegate method; the in-method permission check follows the `analysis:read:all` pattern (`currentUser.has(...)`); mappers named `SourceToTargetMapper` after what they really convert |
| Frontend | frontend-folder-structure.md (`features/settings` API and components, pure logic in `features/<feature>/model/` with a `*.spec.ts`, pages compose features, i18n in `shared/i18n/locales/`) |
| e2e | the existing Playwright setup (`e2e/tests/*.e2e.ts`, the admin session from the global setup, users created over the API and logged in in their own browser context as in `ownership.e2e.ts`, `TA_RUNNER_REPLAY`); the workers share one database |
| Docs | README sections "4. Add an API key", "5. Start an analysis" and "Login"; screenshots with `mise run screenshots` (`e2e/screenshots/*.shot.ts`, 1440×900, dark theme) |

## Design

### Starting an analysis: the server decides

- New orchestration feature `orchestration.analysisstart`:
  - `model/RequestedAnalysis`: the start parameters as the client sent them, the five model/round
    fields nullable (`null` = not sent).
  - `inbound/StartAnalysisWithDefaultsUseCase`: `Analysis start(RequestedAnalysis requested,
    OwnerId owner, boolean mayOverrideModels)`; package-private
    `service/StartAnalysisWithDefaultsService`. (Not `StartAnalysisUseCase`: the analysis domain
    has one, which this one calls.)
    1. The platform values: `ManageAnalysisDefaultsUseCase.get()` (plan 1). It asks the catalog only
       while no defaults are saved.
    2. Every one of the five fields that was sent and differs from its platform value is an
       override. With overrides and `mayOverrideModels` false: throw
       `AnalysisException(AnalysisError.OVERRIDE_FORBIDDEN, ..., Map.of("field", <the first one>))`.
       Sending nothing, or exactly the platform values, is accepted.
    3. With permitted overrides: check them against the catalog (`GetCatalogUseCase`). The provider
       must be in the catalog, and each model must be listed for it (`deepModels` / `quickModels`)
       unless the provider allows custom ids (`customModelAllowed`). (The issue's design said
       "validated as today", but today nothing in the start flow consults the catalog; its
       acceptance criteria ask for the refusal, and the developer chose this rule in the plan
       review.) A failure is `AnalysisException(AnalysisError.INVALID_SPEC, ...,
       Map.of("field", <the field>))`, the 400 `invalid_spec` the start request already answers.
    4. Build the `AnalysisSpec`: each of the five is the override when there is one, else the
       platform value; the other fields with today's mapper defaults (`STOCK`, `English`, `false`).
    5. Call the analysis domain's `StartAnalysisUseCase.start(spec, owner)`, which validates the
       spec (`AnalysisSpecValidator`: formats, rounds 1..10) and stores it as today.
  - The five values are stored in the spec as before, so the run detail, compare view and report
    show the values actually used.
  - **Rerun** (the issue's open question, answered here): unchanged. `RerunAnalysisUseCase` starts the stored
    spec of the original analysis, so a rerun reproduces the original run with its original values,
    whoever reruns it and whatever the platform values are by then. The UI's "Run again" already
    promises "the same settings". It stays on the analysis domain's use case, without a permission
    check of the override: the run it repeats was allowed when it started.
  - A `CatalogUnavailableException` (step 1 while nothing is saved, or step 3) is answered with the
    existing 503 `catalog_unavailable`.
- `AnalysisError` gains `OVERRIDE_FORBIDDEN`; `AnalysisExceptionToApiExceptionMapper` maps it to
  403 (`analysis_override_forbidden`), distinct from the 403 `forbidden` of a missing endpoint
  permission.
- BFF:
  - `StartAnalysisRequest`: `llmProvider`, `deepThinkLlm`, `quickThinkLlm` lose `@NotBlank`; all
    five model/round fields are optional. The other fields keep their constraints.
  - `StartAnalysisRequestToAnalysisSpecMapper` is replaced by
    `StartAnalysisRequestToRequestedAnalysisMapper` (DTO enums to domain enums, the five fields as
    sent, no `maxDebateRounds = 1` default any more).
  - `AnalysesApiDelegateImpl.startAnalysis` calls the new use case with
    `currentUser.has(Permission.ANALYSIS_OVERRIDE_MODELS)`; `@RequiresPermission(ANALYSIS_RUN)`
    stays. `TestSession.validBody` for `POST /api/analyses` stays as it is.

### Frontend

- `features/settings`:
  - `api.ts`: `getAnalysisDefaults()` and `updateAnalysisDefaults(request)`; the
    `AnalysisDefaults` and `SaveAnalysisDefaultsRequest` types in `shared/api/types.ts`, from the
    generated `schema.d.ts` (plan 1); exported from the feature's `index.ts`.
  - `components/AnalysisDefaultsCard.vue`, on `SettingsPage.vue` between the API keys and the
    presets, only with `session.can(PERMISSIONS.analysisDefaultsManage)`: provider select (the
    catalog's providers), deep- and quick-model selects with the chosen provider's models (only
    listed models: the admin check of plan 1), both round inputs (1..10), Save. It loads the values
    on mount and keeps their `version` (`null` before the first save) for the PUT. A 409
    `concurrent_update` shows the message and reloads, as a stale preset rename does; a 400
    `invalid_analysis_defaults` names the field; `catalog_unavailable` shows the existing hint.
- New analysis page (`pages/NewAnalysisPage.vue`):
  - on mount it loads the platform defaults with the catalog; the five fields start from them
    (instead of `catalog.defaults`, which the page applies today).
  - `session.can(PERMISSIONS.analysisOverrideModels)`:
    - without: the five fields are read-only, show the platform values with the hint "Set by the
      administrator", and are left out of the start request;
    - with: they are editable, a "Reset to defaults" link restores the platform values, and the
      start request carries only the fields that differ from them.
  - The round inputs go up to 10 (5 today), the range the backend and the admin card accept, so a
    platform value above 5 is shown and kept as it is.
  - A typed model id (the selects' `tag`) only where the provider allows custom ids
    (`customModelAllowed`), as the start request checks.
  - Presets: with the permission, applying and saving keep the five values as today. Without it,
    applying a preset leaves the five at the platform values, and saving a preset stores the other
    fields only.
  - The pure logic (which fields a start request carries, which preset fields apply or are saved)
    goes to `features/analysis/model/analysisOverrides.ts` with `analysisOverrides.spec.ts`.
- i18n (en and tr): `settings.analysisDefaults.*` (title, note, provider, deep model, quick model,
  debate rounds, risk rounds, save, saved), `form.defaultsHint`, `form.resetToDefaults`,
  `errors.analysis_override_forbidden`, `errors.invalid_analysis_defaults`.

### e2e

- The workers share one database, so every test that saves platform defaults puts the values it
  read back at its end (`finally`), and those tests run serially in one file.
- `settings.e2e.ts`: the admin saves the five values on the card (toast; after a reload they are
  shown).
- New `analysis-defaults.e2e.ts` (`test.describe.configure({ mode: 'serial' })`; a created ANALYST
  user, logged in as in `ownership.e2e.ts`):
  - the admin saves defaults; the ANALYST sees them read-only on New analysis and has no Analysis
    defaults card on Settings; the ANALYST starts the analysis, and its run page shows exactly these
    values;
  - the ANALYST's API start request with another provider → 403 `analysis_override_forbidden`; one
    without the five fields → 201 with the platform values;
  - the admin changes one model for one analysis: the run uses it, and the platform values are
    unchanged afterwards;
  - the ANALYST applies a preset that holds other models: the five values stay.
- The existing tests start their analyses as the admin, who may override, so their requests (with
  `openai` / `gpt-5.4-mini`) keep working.

### Docs

- README "4. Add an API key": a paragraph on the Analysis defaults card (permission
  `analysis:defaults:manage`, the five values, the conflict message, the catalog defaults until an
  admin saves once).
- README "5. Start an analysis": the "Provider, models" and "Debate rounds" rows of the field table
  say where the values come from: read-only without `analysis:override-models`, editable from the
  platform values with it, "Reset to defaults", a typed model id only for providers that allow custom ids; the preset
  sentence says what a preset keeps without the permission; "Run again" keeps the original values.
- README "Login": `POST /api/analyses` decides the five values itself; 403
  `analysis_override_forbidden` (`params.field`) for a differing value without the permission.
- The screenshots below.

## Work packages

### WP1: Orchestration start use case

- **Status**: done
- **Depends on**: none
- **Files**: `orchestration/.../orchestration/analysisstart/model/RequestedAnalysis.java`,
  `inbound/StartAnalysisWithDefaultsUseCase.java`, `service/StartAnalysisWithDefaultsService.java`;
  analysis core `exception/AnalysisError.java` (`OVERRIDE_FORBIDDEN`);
  `bff/impl/.../mapper/error/AnalysisExceptionToApiExceptionMapper.java` (the 403)
- **Steps**:
  - [x] the model with the five nullable fields
  - [x] the use case and its service, steps 1 to 5 above
  - [x] the error code and its 403 mapping
- **Tests**: `StartAnalysisWithDefaultsServiceTest` (unit, fakes for the defaults and catalog use
  cases and the domain's `StartAnalysisUseCase`): nothing sent → the platform values; exactly the
  platform values without the permission → accepted; a differing value without it → 
  `OVERRIDE_FORBIDDEN` with the field, and nothing started; overrides with it → merged with the
  platform values for the rest; an override the catalog refuses → `INVALID_SPEC` with the field;
  the catalog is not asked when there are no overrides and defaults are saved.

### WP2: BFF start endpoint

- **Status**: done
- **Depends on**: WP1
- **Files**: `bff/api/.../model/StartAnalysisRequest.java`;
  `bff/impl/.../mapper/StartAnalysisRequestToRequestedAnalysisMapper.java` (replaces
  `StartAnalysisRequestToAnalysisSpecMapper`); `bff/impl/.../AnalysesApiDelegateImpl.java`;
  `artifact/backend/src/test/.../AnalysesApiIntegrationTest.java`,
  `AnalysisOwnershipIntegrationTest.java`
- **Steps**: as above
- **Tests**: the runner scripts of `AnalysesApiIntegrationTest` and
  `AnalysisOwnershipIntegrationTest` answer `catalog` (the first argument) with the example catalog
  of docs/event-protocol.md section 1, whose defaults (`deepseek`, `deepseek-v4-pro`,
  `deepseek-v4-flash`) are the values their ANALYST requests send, so those requests stay valid;
  `reportsSystemHealth` still finds no `version` command. New cases in `AnalysesApiIntegrationTest`:
  - an ANALYST without the five fields → 201, the spec has the catalog defaults and `1 / 1`;
  - after an admin saved defaults: an ANALYST's run has exactly them; an ANALYST sending another
    provider → 403 `analysis_override_forbidden` with `params.field`; one sending exactly the
    platform values → 201;
  - an ADMIN changing one model → 201, the spec has it and the platform values for the rest, and
    `GET /api/settings/analysis-defaults` is unchanged;
  - an ADMIN override the catalog refuses → 400 `invalid_spec`;
  - an ANALYST's rerun after the defaults changed → the original values.
  `reportsInvalidInputWithAnErrorCodeAndField` drops its "missing `deepThinkLlm` → 400" case (the
  field is optional now) and keeps the format cases.

### WP3: Frontend: Analysis defaults card

- **Depends on**: none
- **Files**: `features/settings/api.ts`, `features/settings/index.ts`,
  `features/settings/components/AnalysisDefaultsCard.vue`, `pages/SettingsPage.vue`,
  `shared/api/types.ts`, `shared/i18n/locales/en.json`, `tr.json`
- **Steps**: the API methods and types; the card (load, save with the version, the 409 reload, the
  provider's models from the catalog store); mounted behind `analysisDefaultsManage`; i18n texts
- **Tests**: the card holds no logic beyond the API calls; the e2e test of WP5 covers it.

### WP4: Frontend: New analysis page

- **Depends on**: WP2, WP3
- **Files**: `pages/NewAnalysisPage.vue`, `features/analysis/model/analysisOverrides.ts`,
  `analysisOverrides.spec.ts`, `features/analysis/index.ts` if the page needs the function through
  it, `shared/i18n/locales/en.json`, `tr.json`
- **Steps**: load the platform defaults on mount; read-only or editable fields; "Reset to
  defaults"; rounds up to 10; `tag` only for providers with custom ids; send only the overrides;
  the preset rules without the permission
- **Tests**: `analysisOverrides.spec.ts`: no fields without the permission; only the differing
  fields with it; none after a reset; the preset fields applied and saved with and without the
  permission.

### WP5: e2e, screenshots, README

- **Depends on**: WP3, WP4
- **Files**: `e2e/tests/settings.e2e.ts`, `e2e/tests/analysis-defaults.e2e.ts` (new),
  `e2e/screenshots/pages.shot.ts` (the retaken shots and the new one), `docs/screenshots/*.png`,
  `README.md`
- **Steps**: the e2e scenarios, the screenshots and the README changes above
- **Tests**: the e2e tests (`mise run e2e`); the screenshots with `mise run screenshots`.

## Tests

WP1's unit tests prove the server-side decision, WP2's integration tests the answers over HTTP,
WP4's spec the frontend's rules, and WP5's e2e tests the issue's acceptance criteria end to end: an
admin's values are what the next ANALYST run uses, visible on the run page; the ANALYST sees them
read-only; a differing request is refused with 403 and one without the fields succeeds; an ADMIN's
override applies to one run only; a refused model in an override; a preset does not change the
ANALYST's values; without saved values the catalog defaults and `1 / 1` (WP2, plan 1); two admins
saving at once (plan 1).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | README.md "4. Add an API key" | the Analysis defaults card |
| Text | README.md "5. Start an analysis" | where the five values come from, with and without `analysis:override-models`; "Reset to defaults"; custom model ids; presets without the permission; "Run again" keeps the original values |
| Text | README.md "Login" | the start request decides the five values; 403 `analysis_override_forbidden` |
| Screenshot | `docs/screenshots/settings.png`, retaken | Settings as the admin: API keys, the Analysis defaults card with the catalog defaults, presets ("4. Add an API key") |
| Screenshot | `docs/screenshots/new-analysis.png`, retaken | the form as the admin, the fields editable from the platform values with "Reset to defaults" ("5. Start an analysis") |
| Screenshot | `docs/screenshots/new-analysis-readonly.png`, new | the form as a created ANALYST user: the five values read-only with "Set by the administrator" ("5. Start an analysis") |

## Out of scope

- The permissions, the stored `AnalysisDefaults`, the `GET` / `PUT /api/settings/analysis-defaults`
  endpoints and their README rows: plan 1.
- Per-user or per-role defaults, limiting which providers or models an overriding user may pick,
  cost estimates (#37).
