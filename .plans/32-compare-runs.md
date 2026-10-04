# Plan: Compare runs side by side

- **Issue**: #32 (Compare runs)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (doc/coding-convention/repository-versioning-and-releases.md)

## Goal

On the Analyses page, the user ticks two or more runs and opens a compare view that shows them side
by side, one column per run: the spec (ticker, date, provider and models, analysts, debate rounds),
the decision (rating and decision text), the stats (LLM and tool calls, tokens, cost, duration), and
the report sections, so the same ticker run with a different model or date can be compared at a
glance.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Frontend (`artifact/frontend`) | `doc/coding-convention/frontend-folder-structure.md`: a route component in `pages/` with its route in `app/router/index.ts`; feature code in `features/analysis/` (components, `model/` for pure logic with `*.spec.ts` tests); other features (reports) reached through their `index.ts` only; only `api.ts` calls the HTTP client; translations in `shared/i18n/locales/en.json` and `tr.json` (both, `locales.spec.ts` checks they match); Prettier formatting, ESLint boundaries |
| End-to-end tests (`e2e/`) | The existing Playwright tests in `e2e/tests/` against the built jar with ta-runner replaying a recording; Prettier formatting |
| Backend | No change: `GET /api/analyses/{id}` and `GET /api/analyses/{id}/report` already return everything the view needs |

## Design

- **Selection on the Analyses page** (`pages/RunsPage.vue`, `features/analysis/components/AnalysisTable.vue`):
  `AnalysisTable` gets an optional `selectable` prop and a `v-model:checked` list of run ids
  (Naive UI `NDataTable` `type: 'selection'` column, `checked-row-keys`). Clicking a row still opens
  the run; the checkbox does not. `RunsPage` shows a **Compare (n)** button in the header, enabled
  for 2 to 4 selected runs, which navigates to the compare route. The dashboard's compact table
  stays without checkboxes.
- **Route**: `/analyses/compare?ids=<id>,<id>[,...]`, name `compare`, component
  `pages/ComparePage.vue`, declared before `/analyses/:id` so `compare` is not taken for an id. The
  ids live in the URL, so a comparison can be bookmarked and shared.
- **Data**: `ComparePage` loads every run with `analysisApi.getAnalysis(id)` and, for completed
  runs, `reportsApi.getReport(id)` (through `@/features/analysis` and `@/features/reports`), in
  parallel. A run that fails to load shows an error in its column; the others still show. Fewer than
  two valid ids shows an empty state with a link back to the Analyses page.
- **Pure model** `features/analysis/model/compareView.ts`: from the loaded runs (and reports), builds
  the rows of the comparison: spec rows, the decision row, stats rows, and for each section in
  `SECTIONS` order the section text per run. It marks the rows whose values differ between runs (so
  the page can highlight them), and keeps sections that exist in at least one run. No Vue or HTTP
  imports.
- **Component** `features/analysis/components/CompareTable.vue`: renders the model as a table with a
  sticky first column (row label) and one column per run (ticker, date and model in the header,
  linking to the run). Differing values are highlighted. Report sections are collapsible
  (`NCollapse`), rendered with `MarkdownView` from `@/features/reports` — passed in by the page
  through a slot, so the analysis feature does not import the reports feature. Exported from
  `features/analysis/index.ts`.
- Stats use the same formatting as the run page (`useLabels`: `duration`, integers, cost with the
  existing helpers).

## Work packages

### WP1: Compare model

- **Depends on**: none
- **Files**: `artifact/frontend/src/features/analysis/model/compareView.ts`,
  `artifact/frontend/src/features/analysis/model/compareView.spec.ts`
- **Steps**:
  - [ ] Types for the comparison rows (`label key`, values per run, `differs` flag) and a
        `compareView(runs, reports, sections)` function.
  - [ ] Spec, decision and stats rows; section rows in the given order, skipping sections absent
        from every run.
- **Tests**: Vitest: rows for two and for three runs, the `differs` flag (same model vs different
  model), a run without a report (failed or still running), a section present in one run only.

### WP2: Selection on the Analyses page

- **Depends on**: none
- **Files**: `artifact/frontend/src/features/analysis/components/AnalysisTable.vue`,
  `artifact/frontend/src/pages/RunsPage.vue`, `artifact/frontend/src/shared/i18n/locales/en.json`,
  `artifact/frontend/src/shared/i18n/locales/tr.json`
- **Steps**:
  - [ ] `selectable` prop and `v-model:checked` (selection column, row keys = run ids).
  - [ ] **Compare (n)** button on `RunsPage`, enabled for 2–4 selected runs, navigating to
        `{ name: 'compare', query: { ids } }`; the selection survives the polling refresh.
  - [ ] Translations: `runs.compare`, `runs.compareHint` (en and tr).
- **Tests**: covered by the e2e test of WP4.

### WP3: Compare page

- **Depends on**: WP1
- **Files**: `artifact/frontend/src/pages/ComparePage.vue`,
  `artifact/frontend/src/features/analysis/components/CompareTable.vue`,
  `artifact/frontend/src/features/analysis/index.ts`, `artifact/frontend/src/app/router/index.ts`,
  `artifact/frontend/src/shared/i18n/locales/en.json`, `artifact/frontend/src/shared/i18n/locales/tr.json`
- **Steps**:
  - [ ] Route `compare` before `analysis`; `meta: { wide: true }` like the reports page.
  - [ ] `ComparePage`: parse `ids`, load runs and reports in parallel, per-column errors, empty
        state.
  - [ ] `CompareTable` with sticky row labels, run headers linking to the runs, highlighted
        differences, collapsible sections rendered through a slot with `MarkdownView`.
  - [ ] Translations: a `compare` group (title, row labels, empty state, load error) in en and tr.
- **Tests**: `pnpm type-check` and ESLint (boundaries) pass; behaviour covered by WP1's unit tests
  and WP4's e2e test.

### WP4: End-to-end test

- **Depends on**: WP2, WP3
- **Files**: `e2e/tests/compare.e2e.ts`
- **Steps**:
  - [ ] Start two analyses with the replayed recording (as `analysis.e2e.ts` does), wait for both to
        complete, select both on the Analyses page, press **Compare (2)**, and check that the compare
        view shows two columns, the decision row and a report section.
- **Tests**: the new e2e test passes in `mise run e2e`.

### WP5: Agents document changes, screenshots included

- **Depends on**: none (moved here from #69 at the developer's request, so it ships with the first
  change it was found on)
- **Files**: `e2e/playwright.screenshots.config.ts`, `e2e/screenshots/support.ts`,
  `e2e/package.json`, `e2e/tsconfig.json`, `mise.toml`, `.claude/skills/plan-from-issue/*`,
  `scripts/agent/prompts/*`, `doc/agentic-development.md`, `README.md`
- **Steps**:
  - [x] `mise run screenshots [filter]`: Playwright screenshots like the e2e tests run (built jar,
        replayed run, Chrome, 1440×900, dark theme), one `e2e/screenshots/<name>.shot.ts` each.
  - [x] Planning skill and template: "Docs to update" lists every text and screenshot.
  - [x] Developer agent takes the screenshots; review agent checks docs and screenshots (`MAJOR`).
  - [x] Take `compare.png` and retake `analyses.png` with it (the two screenshots above).
- **Tests**: `pnpm run type-check` in `e2e/`; the screenshots themselves, looked at.

## Tests

- Unit: `compareView.spec.ts` (Vitest, part of `./gradlew :frontend:build` and CI).
- End to end: `e2e/tests/compare.e2e.ts` (`mise run e2e`, CI's e2e job).
- `mise run check` (ESLint boundaries, Prettier, type-check of the frontend and e2e).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, section "6. Past analyses" | A short paragraph on selecting runs and comparing them |
| Text | `README.md`, CI table, end-to-end tests row | Mention comparing two runs |
| Screenshot | `docs/screenshots/compare.png`, new | The compare view with two completed runs (two replayed analyses, e.g. NVDA and MU), the decision row and an opened report section visible; shown in "6. Past analyses" under the new paragraph |
| Screenshot | `docs/screenshots/analyses.png`, retaken | The Analyses list with a few completed runs, two of them ticked and the **Compare (2)** button enabled; replaces the current one in "6. Past analyses" |

Added after the first two review rounds (the first version of this plan deferred the screenshots,
which left the pull request undocumented; see WP5).

## Out of scope

- Comparing more than four runs, and comparing runs from the Reports page.
- A textual diff inside report sections (only side-by-side text and differing-row highlight).
- New backend endpoints: the view uses the existing ones.

## Open questions

- Is four runs the right upper limit for the side-by-side layout, or should it scroll horizontally
  for more?
