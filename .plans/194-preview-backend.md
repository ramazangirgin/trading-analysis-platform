# Plan: Partial visibility and the public preview API

- **Issue**: #194 (Partially public analyses on the main page: a preview for visitors, the full analysis after sign-up)
- **Plan**: 1 of 2 for this issue; depends on: none (#192 sharing is merged)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): new endpoints and
  a new value of `visibility`, no existing request or answer changes

## Goal

The owner of a completed analysis who holds `analysis:share` can make it **partial**. Logged-in users
see a partial analysis in full, exactly like a public one. A visitor who is not logged in can call two
new endpoints without a session, `GET /api/public/analyses` and `GET /api/public/analyses/{id}`, and
gets a **preview**: the ticker, the date, the rating, the configured sections, and the titles of the
sections it hides. The hidden parts are cut on the server and never sent. The endpoints are rate-limited
per client address. The user interface is the next plan (`194-preview-frontend`).

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend: analysis domain (core, persistence adapter) | `backend-java-package-structure.md`: `Visibility.PARTIAL` and the page record in `core.model`, the two visitor use cases in `core.inbound`, their package-private service in `core.service`; `backend-java-persistence.md`: the PostgreSQL enum type gets the value (never text), list filters as `Specification`s; `backend-database-naming.md`: migration `V5__add_partial_visibility.sql`, uppercase quoted names |
| Backend: orchestration | `backend-java-package-structure.md`: a use case that spans two domains (analysis and report) lives in `orchestration`, one package per feature: `orchestration.preview` with `inbound` and `model` |
| Backend: BFF | `backend-java-rest-api.md`: both new endpoints answer only `200` and return their DTO, never `ResponseEntity`; errors are thrown (`AnalysisException` `NOT_FOUND` through `DomainExceptionHandler`), no `try` / `catch` in the delegate; `*EnumDto` for the rating; `TAP-A1`: `@NoPermissionRequired("…")` on both; DTOs in `bff.controller.api.model`, mappers in `bff.delegate.impl.mapper` (`SourceToTargetMapper`, one `map` method) |
| Shared types | `repository-shared-types.md`: `VisibilityEnumDto` gets `PARTIAL` (last); `openapi.json` regenerated against a throwaway PostgreSQL, `mise run api-types` for `schema.d.ts`, both committed |
| Docs | Root `README.md`, `docs/coding-convention/backend-java-persistence.md` is unchanged |

## Design

### Where the preview is built

The issue puts the preview use cases in the analysis domain. The preview is made of the analysis and its
report, and the `analysis` domain must not depend on `report`, so the pieces are split the way
`GetAnalysisReportUseCase` already is:

- the analysis domain answers "which analyses may a visitor see" (`PARTIAL` ones, no user involved);
- `orchestration.preview` reads the report of each and cuts it to the configured parts.

### Analysis domain

- `Visibility`: `PRIVATE`, `PUBLIC`, `PARTIAL` (last, the database enum appends). A method
  `sharedWithUsers()` is true for `PUBLIC` and `PARTIAL`.
- `AnalysisAccess` (the one place the rules are written):
  - `canSee`: `allUsers`, the owner, or `visibility().sharedWithUsers()`;
  - `canManage`: unchanged;
  - `canChangeTo(analysis, target)` replaces `canMakePublic`, `canMakePrivate` and `canChangeVisibility`:
    - to `PRIVATE`: the analysis is not private, and the caller is the owner, or `allUsers && share`;
    - to `PUBLIC`: the analysis is not public, and the caller is the owner with `share`, or
      (the analysis is `PARTIAL` and `allUsers && share`);
    - to `PARTIAL`: the analysis is not partial, and the caller is the owner with `share` (an admin never
      exposes someone else's analysis to the internet, only narrows it);
  - `allowedVisibilities(analysis)`: the targets for which `canChangeTo` is true, in enum order.
- `AnalysisFilter.visibility` (one value) becomes `Set<Visibility> visibilities` (empty: no criterion);
  `publicOfOthersThan(user)` filters on `PUBLIC` and `PARTIAL`, so the **Shared with everyone** list holds
  both. A new `partial()` filter for the visitor list.
- `AnalysisVisibilityService.change`, steps in order (the first three as today):
  1. No analysis, or not `canSee`: `NOT_FOUND`.
  2. `version` differs: `CONCURRENT_UPDATE`.
  3. Already the requested visibility: returned unchanged, no write.
  4. Not `canChangeTo`: for `PRIVATE` or an analysis the caller does not own and the target is not
     `PRIVATE`/`PUBLIC`-from-`PARTIAL`: `NOT_FOUND`; the owner without `share` asking for `PUBLIC` or
     `PARTIAL`: `SHARE_FORBIDDEN`.
  5. A target other than `PRIVATE` and the status is not `COMPLETED`: `NOT_SHAREABLE` (param `status`).
  6. `repository.update(analysis.withVisibility(target, clock.instant()))`, an
     `OptimisticLockingFailureException` becomes `CONCURRENT_UPDATE`.
- New `core.inbound.GetPartialAnalysisUseCase.get(AnalysisId id)` and
  `ListPartialAnalysesUseCase.list(int page, int size)`, implemented by a new package-private
  `core.service.PartialAnalysisService`. `get` returns the analysis only when it is `PARTIAL` (and
  `COMPLETED`), any other case is `NOT_FOUND`, so a private or `PUBLIC` analysis is not revealed. `list`
  returns `PartialAnalysisPage(List<Analysis> items, boolean hasMore)`, newest first.
- `AnalysisRepositoryPort.findPage(AnalysisFilter filter, int page, int size)` returns the page; the
  adapter asks for `size + 1` rows to know `hasMore` (no count query).

### Persistence

- `V5__add_partial_visibility.sql`: `ALTER TYPE "ANALYSIS"."VISIBILITY" ADD VALUE 'PARTIAL';`. Existing rows
  are unchanged. The existing `ANALYSES_VISIBILITY_IDX` serves the visitor list.
- `JpaAnalysisRepositoryAdapter`: the filter's set becomes a `hasVisibilityIn` `Specification`; `findPage`
  uses a `PageRequest` sorted like `findAll`.

### Orchestration: `orchestration.preview`

- Properties `platform.analysis.preview.*`, bound to a record `PreviewSettings`:
  - `sections`: the report sections a visitor reads in full, default `market_report` (the binder is lenient
    about case); an unknown name fails startup;
  - `excerpt-length`: characters of the first allowed section shown on a list card, 1 to 5000, default 400.
  Each has an environment variable in `application.properties` (`PLATFORM_ANALYSIS_PREVIEW_SECTIONS`,
  `PLATFORM_ANALYSIS_PREVIEW_EXCERPT_LENGTH`), as the other `platform.*` settings.
- `model.AnalysisPreview(AnalysisId id, String ticker, LocalDate analysisDate, Rating rating,
  Map<ReportSection, String> sections, List<String> lockedSections)` and
  `AnalysisPreviewSummary(id, ticker, analysisDate, rating, excerpt)`; `model.AnalysisPreviewPage`.
  `lockedSections` holds the keys of every section and debate speaker the report has and the settings do
  not allow (`sentiment_report`, `bull`, …), nothing else: no text, no sources, no prices.
- `inbound.GetAnalysisPreviewUseCase.get(AnalysisId)` and `ListAnalysisPreviewsUseCase.list(page, size)`
  (public, package-private constructors, as `GetAnalysisReportUseCase`): ask the analysis use cases, read
  the report through `GetReportUseCase`, cut it. A report that is missing gives a preview with no
  sections and no locked titles. The excerpt is cut at the last line break before `excerpt-length`, with
  an ellipsis.
- The owner is never read: `AnalysisPreview` has no field for it.

### BFF and the API

- A new `PublicAnalysesApiController` / `PublicAnalysesApiDelegate` pair (its own tag, so the open routes
  are apart from `/api/analyses`), implemented by `PublicAnalysesApiDelegateImpl`:
  - `GET /api/public/analyses?page=0&size=12`: `200` with `AnalysisPreviewPageDto { items:
    AnalysisPreviewSummaryDto[], hasMore }`; `page` is at least 0, `size` 1 to 24 (a violation is the
    existing 400);
  - `GET /api/public/analyses/{id}`: `200` with `AnalysisPreviewDto { id, ticker, analysisDate, rating
    (RatingEnumDto), sections (Map<String,String>, keys like the report's), lockedSections }`; anything
    but a partial analysis is `NOT_FOUND` through `DomainExceptionHandler` (404, `not_found`).
  Both `@NoPermissionRequired("open to visitors: …")`. A logged-in caller gets the same answer.
- `SecurityConfig`: `permitAll` for `GET /api/public/**` before the `/api/**` rule. Every other method on
  that path stays authenticated. `/api/analyses/**` still answers 401 to a visitor.
- Rate limit: `PublicRateLimitFilter` and a `PublicRequestLimiter` next to `LoginRateLimitFilter`, in
  `bff.delegate.impl.config`. Unlike the login limiter it counts **every** request of an address to
  `/api/public/**` in a sliding window; over the limit answers `429` `too_many_requests` with
  `Retry-After`, written by `ApiErrorWriter`. Settings `platform.public.rate-limit.max-requests` (default
  60) and `platform.public.rate-limit.window` (default `1m`), validated like the login's, at most 10,000
  addresses tracked. The client address is `getRemoteAddr()`, as for the login. The filter is added before
  the CSRF filter; it needs no CSRF token since only `GET` is open.
- `VisibilityEnumDto.PARTIAL`, `VisibilityEnumDtoToVisibilityMapper` and its reverse map it (MapStruct
  enum mapping). `AnalysisDto` gets `allowedVisibilities` (`VisibilityEnumDto[]`); `canChangeVisibility`
  stays and is true when the list is not empty. `PUT /api/analyses/{id}/visibility` is unchanged apart
  from accepting `PARTIAL`.
- `openapi.json` and `shared/api/schema.d.ts` regenerated.

## Work packages

### WP1: Partial visibility in the analysis domain

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/analysis/core/src/main/java/.../analysis/core/model/`: `Visibility.java`, `AnalysisAccess.java`, `AnalysisFilter.java`, `PartialAnalysisPage.java` (new)
  - `.../analysis/core/inbound/`: `GetPartialAnalysisUseCase.java`, `ListPartialAnalysesUseCase.java` (new)
  - `.../analysis/core/outbound/persistence/AnalysisRepositoryPort.java`
  - `.../analysis/core/service/`: `AnalysisVisibilityService.java`, `AnalysisListService.java`, `PartialAnalysisService.java` (new)
  - `artifact/backend/domain/analysis/adapter/src/main/java/.../persistence/JpaAnalysisRepositoryAdapter.java`
  - `artifact/backend/domain/analysis/adapter/src/main/resources/.../persistence/migration/V5__add_partial_visibility.sql` (new)
  - every caller of `canMakePublic`, `canMakePrivate`, `canChangeVisibility` and `AnalysisFilter.visibility` (the compiler names them)
- **Steps**:
  - [x] `PARTIAL`, `sharedWithUsers`, `canChangeTo`, `allowedVisibilities`, the set filter.
  - [x] The visibility service with the rules above; the visitor use cases and the paged port method.
  - [x] The migration and the adapter.
- **Tests**:
  - `AnalysisAccessTest`: every cell of `canSee` and `canChangeTo` (owner, other user, admin with and
    without `share`; private, public, partial; each target), `allowedVisibilities`.
  - `AnalysisVisibilityServiceTest`: the owner with `share` makes a completed analysis partial, then
    public, then private; a running, queued, failed or stopped one is `NOT_SHAREABLE` for `PARTIAL`; the
    owner without `share` gets `SHARE_FORBIDDEN` for `PARTIAL` and can make it private; an admin with
    `share` moves another user's `PARTIAL` to `PUBLIC` and to `PRIVATE` but gets `NOT_FOUND` making
    anyone's analysis `PARTIAL`; a stale version and the same visibility as before.
  - `PartialAnalysisServiceTest` (new): `get` answers a partial analysis and `NOT_FOUND` for a private
    and a `PUBLIC` one and for an unknown ID; `list` pages newest first and sets `hasMore`; a private or
    public analysis is never listed.
  - `AnalysisServiceTest` (list): `SHARED` holds `PUBLIC` and `PARTIAL` analyses of other users; a
    partial analysis is readable by another logged-in user and stop, rerun and the log are `NOT_FOUND`.
  - `JpaAnalysisRepositoryAdapterTest`: `PARTIAL` is stored and read back, `findAll` by a visibility set,
    `findPage` with `hasMore`.
  - A test that the database enum mirrors `Visibility` (as the identity one does for `Permission`) and
    `V5AnalysisPartialVisibilityMigrationTest`: a row written at V4 reads the same after V5.
  - `AnalysisPersistenceConventionsTest` passes unchanged.

### WP2: The preview in orchestration

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/orchestration/src/main/java/.../orchestration/preview/inbound/`: `GetAnalysisPreviewUseCase.java`, `ListAnalysisPreviewsUseCase.java`, `PreviewSettings.java`; `model/`: `AnalysisPreview.java`, `AnalysisPreviewSummary.java`, `AnalysisPreviewPage.java`
  - `artifact/backend/src/main/resources/application.properties`
  - tests named below
- **Steps**:
  - [x] The settings record with validation, the two use cases and the cut.
- **Tests**:
  - `GetAnalysisPreviewUseCaseTest`: with the default settings the preview holds `market_report` only
    and lists every other section and debate speaker of the report in `lockedSections`; a changed
    `sections` setting changes both; no text of a hidden part appears anywhere in the result; a report
    that is missing; a non-partial analysis is `NOT_FOUND`.
  - `ListAnalysisPreviewsUseCaseTest`: the excerpt is cut at a line break, at most `excerpt-length`
    characters; paging passes through.
  - `PreviewSettingsTest`: defaults, an unknown section and an out-of-range excerpt length fail.

### WP3: The public endpoints, rate limit and API contract

- **Depends on**: WP2
- **Files**:
  - `artifact/backend/bff/api/src/main/java/.../bff/controller/api/`: `PublicAnalysesApiController.java`, `PublicAnalysesApiDelegate.java`; `model/`: `AnalysisPreviewDto.java`, `AnalysisPreviewSummaryDto.java`, `AnalysisPreviewPageDto.java` (new), `VisibilityEnumDto.java`, `AnalysisDto.java`
  - `artifact/backend/bff/impl/src/main/java/.../bff/delegate/impl/`: `PublicAnalysesApiDelegateImpl.java`; `config/`: `SecurityConfig.java`, `PublicRateLimitFilter.java`, `PublicRequestLimiter.java`; `mapper/`: `AnalysisPreviewToAnalysisPreviewDtoMapper.java`, `AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper.java`, `AnalysisToAnalysisDtoMapper.java`, the two visibility enum mappers
  - `artifact/backend/src/main/resources/application.properties`
  - `artifact/frontend/openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` (regenerated; `schema.d.ts` is never edited by hand)
- **Steps**:
  - [ ] DTOs, controller, delegate, mappers.
  - [ ] The security rule, the limiter and the filter.
  - [ ] `allowedVisibilities` on `AnalysisDto`.
  - [ ] Regenerate `openapi.json` against a throwaway PostgreSQL, run `mise run api-types`.
- **Tests**:
  - `PartialPreviewIntegrationTest` (new, `artifact/backend/src/test/...`, as `AnalysisSharingIntegrationTest`;
    owner with PUBLISHER, another user, the admin, and a request without a session):
    - the owner makes a completed analysis partial (200, `visibility` `PARTIAL`), a running one gets 409
      `analysis_not_shareable`, a user without `analysis:share` gets 403 `analysis_share_forbidden`;
    - a visitor lists it and opens `GET /api/public/analyses/{id}` with no session and no CSRF token:
      the body has the ticker, date, rating and `market_report`, and **no hidden section, debate, source,
      price or owner** (the raw JSON is searched for the hidden text, the username and the owner ID);
    - a visitor gets 404 for a private and for a `PUBLIC` analysis, and 401 for `/api/analyses`,
      `/api/analyses/{id}`, `…/report`, `…/prices`, `…/events` and `POST /api/public/analyses`;
    - a logged-in user gets the same preview, lists the analysis under `scope=SHARED`, reads it in full
      and gets 404 on its log, stop and rerun;
    - the owner makes it private: the visitor's list is empty and its preview is 404;
    - the admin moves another user's partial analysis to `PUBLIC` and to `PRIVATE`, and gets 404 making
      it partial; `allowedVisibilities` shows each caller's options;
    - a changed `platform.analysis.preview.sections` (a second context, `@TestPropertySource`) shows
      another section.
  - `PublicRateLimitFilterTest` and `PublicRequestLimiterTest` (as `LoginRateLimitFilterTest`): the
    request over the limit is 429 with `Retry-After`, the window frees up, addresses are independent,
    other paths and methods are not counted.
  - `EndpointPermissionsIntegrationTest`: the two new routes join `OPEN`.
  - `ArchitectureTest` passes (`orchestration.preview` depends on both domains' core only).

## Tests

- Unit: `AnalysisAccessTest`, `AnalysisVisibilityServiceTest`, `PartialAnalysisServiceTest`, the preview
  use case tests and the limiter tests carry the rules.
- Adapter: `JpaAnalysisRepositoryAdapterTest`, the migration test and the conventions test (PostgreSQL
  through Testcontainers).
- Integration: `PartialPreviewIntegrationTest` proves the issue's backend acceptance criteria: only the
  owner with `analysis:share` makes a completed analysis partial, the visitor's preview holds nothing
  hidden, 404 for a private or public one, 401 for `/api/analyses/**`, 404 after making it private, 429
  over the limit, configuration changes the preview.
- `mise run check` passes.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "6. Watch it and read the results" (the sharing paragraph) | The third option **Partial**: what a visitor sees, what only a logged-in user sees; who may set it |
| Text | `README.md`, "Login" (endpoint table and the open routes) | `GET /api/public/analyses` and `GET /api/public/analyses/{id}` need no session, their answers (200, 404, 429) and the rate limit; `PUT …/visibility` accepts `PARTIAL` |
| Text | `README.md`, configuration table | `platform.analysis.preview.sections`, `…excerpt-length`, `platform.public.rate-limit.max-requests`, `…window` with their environment variables and defaults |
| Text | `artifact/frontend/openapi.json`, `shared/api/schema.d.ts` | regenerated |
| Screenshot | none | no page changes in this plan; the new pages and screenshots are in plan 2 |

`docs/event-protocol.md` and the coding conventions: unchanged, nothing new in them.

## Out of scope

- Every page, route, component and e2e test: plan 2, `194-preview-frontend`.
- The "Create an account" step: registration is #186, which is still open (see Open questions).
- Choosing the preview parts per analysis, SEO and Open Graph cards, expiring links, visit statistics
  (the issue's own list).
- Audit of a change to `PARTIAL`: with the audit log of #93, which is not there yet.

## Open questions

- **#186 (registration) is not merged**, and the platform has no way to create an account for a
  visitor: an admin creates users today. This plan needs none of it. The frontend plan decides what the
  call to action does until #186 lands (see its open question).
- **Preview configuration needs a restart.** `platform.analysis.preview.*` is read at startup, like the
  other `platform.*` settings, so "without a release" means a changed environment variable and a
  restart. The alternative, a setting stored in the database and editable in the UI, is a feature of its
  own. The plan assumes **restart**.
- **Rate limit defaults**: 60 requests per address per minute. The landing page makes one list call and
  a preview page one call, so this is generous for a person and tight for a scraper. The plan assumes
  these defaults.
- **Locked titles as keys**: `lockedSections` carries report keys (`sentiment_report`, `bull`), and the
  frontend translates them, so the API holds no language. The issue says "titles"; the plan assumes
  **keys**.
- **Analysis ID in the preview URL**: an ID is how a visitor opens a preview, as for the existing
  routes; it is not secret, and a partial analysis is public to the internet by the owner's choice.
