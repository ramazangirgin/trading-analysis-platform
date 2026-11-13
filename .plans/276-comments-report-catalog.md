# Plan: Comments cleanup: report and catalog

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 6 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`); implemented after plan 5 (`276-comments-identity`), as every cleanup plan edits the temporary filters in `config/checkstyle/checkstyle.xml`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `domain/report/core`, `domain/report/adapter`, `domain/catalog/core` and `domain/catalog/adapter` follow
[repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): what a reader
would lose stays, as a `//` comment, and the rest is gone. The plan's temporary filter is removed,
so `TAP-J1` reports any new Javadoc in them.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md),
  [backend-java-checkstyle.md, "Temporary filters (#276)"](../docs/coding-convention/backend-java-checkstyle.md).

## Design

- Every Javadoc and `//` comment of the listed files is deleted, kept, or (the reason a Javadoc
  held) rewritten as a `//` comment, as `repository-code-comments.md` says. Nothing else changes,
  except an import that only a deleted Javadoc's `{@link}` used (`UnusedImports`).
- The files are those of the modules that held a Javadoc or a `//` comment on `main` at
  `753c8699`. A path in a work package is relative to the Java package root of its module (named
  in the work package's title), in `src/main/java` unless marked `(test)` or `(testFixtures)`:
  - `domain/report/core`: `artifact/backend/domain/report/core/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/report/core/`
  - `domain/report/adapter`: `artifact/backend/domain/report/adapter/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/report/adapter/`
  - `domain/catalog/core`: `artifact/backend/domain/catalog/core/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/catalog/core/`
  - `domain/catalog/adapter`: `artifact/backend/domain/catalog/adapter/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/catalog/adapter/`
- The last package deletes the block `Temporary (#276, plan 6)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `domain/report/core`: inbound, model, part 1 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `inbound/CheckReportDecisionPort.java`, `inbound/GetPriceHistoryPort.java`, `inbound/GetReportPort.java`, `inbound/ScanReportsPort.java`, `inbound/ScanRunHistoryPort.java`, `inbound/WatchDataDirPort.java`, `model/PriceBar.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:report:core:test` and `mise run check` pass.

### WP2: `domain/report/core`: model, part 2 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `model/PriceHistory.java`, `model/PricePoint.java`, `model/Report.java`, `model/ReportContent.java`, `model/ReportKey.java`, `model/ReportSection.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:report:core:test` and `mise run check` pass.

### WP3: `domain/report/core`: model, outbound, part 3 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `model/ReportSource.java`, `model/RunHistoryEntry.java`, `outbound/datadir/DataDirPort.java`, `outbound/datadir/DataDirWatchPort.java`, `outbound/history/RunHistoryPort.java`, `outbound/prices/PriceCachePort.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:report:core:test` and `mise run check` pass.

### WP4: `domain/report/core`: service (main, tests), part 4 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `service/DataDirWatchService.java`, `service/DecisionRatingService.java`, `service/PriceHistoryService.java`, `service/ReportService.java`, `service/DataDirWatchServiceTest.java` (test), `service/PriceHistoryServiceTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:report:core:test` and `mise run check` pass.

### WP5: `domain/report/adapter`: datadir, history, prices (main, tests)

- **Status**: open
- **Depends on**: none
- **Files**: `datadir/FileSystemDataDirAdapter.java`, `datadir/FileSystemDataDirWatchAdapter.java`, `datadir/json/FullStateLog.java`, `history/JsonRunHistoryAdapter.java`, `prices/CsvPriceCacheAdapter.java`, `datadir/FileSystemDataDirAdapterTest.java` (test), `datadir/FileSystemDataDirWatchAdapterTest.java` (test), `history/JsonRunHistoryAdapterTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:report:adapter:test` and `mise run check` pass.

### WP6: `domain/catalog/core`: exception, inbound, model, part 1 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `exception/CatalogUnavailableException.java`, `inbound/GetCatalogPort.java`, `inbound/GetEngineVersionPort.java`, `model/Catalog.java`, `model/EngineVersion.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:catalog:core:test` and `mise run check` pass.

### WP7: `domain/catalog/core`: model, outbound, service, part 2 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `model/ModelDefaults.java`, `model/ModelOption.java`, `model/Provider.java`, `outbound/runner/EngineInfoPort.java`, `service/CatalogService.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:catalog:core:test` and `mise run check` pass.

### WP8: `domain/catalog/adapter`: runner (main, tests)

- **Status**: open
- **Depends on**: none
- **Files**: `runner/DockerEngineInfoAdapter.java`, `runner/TaRunnerEngineInfoAdapter.java`, `runner/json/CatalogJson.java`, `runner/json/VersionJson.java`, `runner/support/DockerClients.java`, `runner/support/RunnerKind.java`, `runner/DockerEngineInfoAdapterTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:catalog:adapter:test` and `mise run check` pass.

### WP9: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 6)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 6 goes |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
