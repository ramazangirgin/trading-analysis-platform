# Frontend: folder structure

Where each kind of module lives in the Vue app (`artifact/frontend/src`), what may import what, and
which ESLint rule in [`eslint.config.js`](../../artifact/frontend/eslint.config.js) guards it.

## Layout

```
src/
  app/              the shell: main.ts (entry point), App.vue (layout, menu), router/
  pages/            one route component per route: compose features, hold page state
  features/
    analysis/       start, list and follow runs
    reports/        report text, prices chart, export, import of existing runs
    settings/       provider keys, presets
    health/         the platform's health checks
    catalog/        providers, models and analysts the runner offers
      index.ts      the feature's public API: the only file other folders import
      api.ts        the feature's backend calls: the only place that calls the HTTP client
      store.ts      a Pinia store, when the feature needs one
      components/   the feature's Vue components
      composables/  the feature's composables (useRunStream, useReportExport, ...)
      model/        pure TypeScript: view logic without Vue or HTTP, with its *.spec.ts tests
  shared/           used by every folder above, knows none of them
    api/            client.ts (HTTP), error.ts (ApiError), types.ts (API data shapes),
                    schema.d.ts (generated from openapi.json, never edited)
    ui/             generic components (StatusTag, RatingTag, LocaleSwitcher)
    charts/         ECharts setup and useChart
    i18n/           vue-i18n setup, locales/*.json
    composables/    generic composables (usePolling, useLabels)
```

A feature has only the folders it needs. `@/` points at `src/` (`tsconfig.json`,
`vite.config.ts`).

## Dependencies

```
app  ->  pages  ->  features  ->  shared
```

| From \ To | `app` | `pages` | `features/*` | `shared/*` |
|---|---|---|---|---|
| `app` | yes | yes | `index.ts` only | yes |
| `pages` | no | yes (same folder) | `index.ts` only | yes |
| `features/x` | no | no | own files: yes; other feature: `index.ts` only | yes |
| `shared/*` | no | no | no | yes |

- Nothing imports upwards: `shared` knows no feature, a feature knows no page.
- A page or another feature reaches a feature only through its `index.ts`. What `index.ts` does not
  export is private to the feature. Features avoid importing each other at all; if two need the same
  thing it usually belongs in `shared`.
- Only a feature's `api.ts` imports `@/shared/api/client`. Components, composables, stores, pages
  and `shared` get data through a feature's `api.ts`, store or composables.
- Everyone may import the types in `@/shared/api/types` and `ApiError` from `@/shared/api/error`;
  only `types.ts` imports the generated `schema.d.ts`.

## Where things go

| Kind | Place |
|---|---|
| Route component | `pages/*Page.vue`; the route in `app/router/index.ts` |
| Backend call | `features/<feature>/api.ts`, as a method of `<feature>Api` |
| State shared across pages | `features/<feature>/store.ts` (Pinia) |
| Component of one feature | `features/<feature>/components/` |
| Component with no feature knowledge | `shared/ui/` |
| Composable of one feature | `features/<feature>/composables/` |
| Generic composable | `shared/composables/` |
| Pure logic (parsing, derived view data) | `features/<feature>/model/`, without Vue or HTTP imports |
| API data shape | `shared/api/types.ts` (derived from `schema.d.ts`) |
| Translation | `shared/i18n/locales/<locale>.json` |
| Test | next to the module it tests: `<module>.spec.ts` (Vitest) |
| Lazily loaded component | exported from the feature's `index.ts` as `defineAsyncComponent(() => import('./components/X.vue'))`, so callers keep the code split (e.g. `PriceChart`, which pulls in ECharts) |

Pages still carry page-level state and logic (`ReportsPage.vue`, `RunDetailPage.vue`). New logic
that belongs to a feature goes into that feature's composables or model instead, and the pages are
meant to shrink that way over time.

## Enforcement

| Rule | ESLint rule |
|---|---|
| The dependency matrix above; features and pages entered through `index.ts` only | `boundaries/dependencies` (elements `app`, `pages`, `feature`, `shared` in `boundaries/elements`) |
| Every file under `src/` belongs to one of those folders | `boundaries/no-unknown-files` |
| Only `features/*/api.ts` imports `shared/api/client`; only `shared/api` imports `schema` | `no-restricted-imports` (turned off for `src/features/*/api.ts` and `src/shared/api/**`) |

Imports inside one element (one feature, one `shared` segment, `pages`, `app`) are not checked by
`boundaries/dependencies`; use relative paths there.

ESLint runs in `pnpm lint` (Gradle `:frontend:pnpmLint`, part of `mise run build` and CI) and in the
pre-commit hook on staged files. `@/` imports resolve through `eslint-import-resolver-typescript`
and `tsconfig.json`.
